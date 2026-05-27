package com.rag.service.review.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.constant.KafkaConstants;
import com.rag.common.enums.DocumentStatus;
import com.rag.common.enums.ReviewResult;
import com.rag.common.enums.TaskType;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.communication.kafka.dto.KafkaMessage;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.DocumentChunk;
import com.rag.domain.entity.ReviewRecord;
import com.rag.domain.entity.SystemConfig;
import com.rag.domain.mapper.DocumentChunkMapper;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.domain.mapper.ReviewRecordMapper;
import com.rag.domain.mapper.SystemConfigMapper;
import com.rag.infrastructure.config.MinioConfig;
import com.rag.infrastructure.lock.RedisLockUtil;
import com.rag.service.review.ReviewService;
import com.rag.service.review.dto.ReviewSubmitDTO;
import com.rag.service.review.dto.ReviewVO;
import com.rag.service.statemachine.DocumentStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRecordMapper reviewRecordMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final DocumentStateMachine stateMachine;
    private final RedisLockUtil redisLockUtil;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MinioConfig minioConfig;

    @Override
    public Result<Page<ReviewVO>> getPendingList(Integer page, Integer size, String result) {
        String filter = result != null ? result : ReviewResult.PENDING.name();
        return getPagedReviewRecords(filter, page, size);
    }

    @Override
    public Result<Page<ReviewVO>> getChunkReviewList(Integer page, Integer size) {
        // Query documents in CHUNK_REVIEW status directly
        Page<Document> pg = new Page<>(page, size);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getStatus, DocumentStatus.CHUNK_REVIEW.name())
                .orderByDesc(Document::getUpdatedAt);

        Page<Document> result = documentMapper.selectPage(pg, wrapper);
        Page<ReviewVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<ReviewVO> voList = result.getRecords().stream().map(doc -> {
            ReviewVO vo = new ReviewVO();
            vo.setId(doc.getId());
            vo.setDocumentId(doc.getId());
            vo.setDocumentName(doc.getFileName());
            vo.setDocumentStatus(doc.getStatus());
            vo.setChunkCount(doc.getChunkCount());
            vo.setCreatedAt(doc.getCreatedAt());
            return vo;
        }).toList();
        voPage.setRecords(voList);
        return Result.success(voPage);
    }

    private Result<Page<ReviewVO>> getPagedReviewRecords(String resultFilter, Integer page, Integer size) {
        Page<ReviewRecord> pg = new Page<>(page, size);
        LambdaQueryWrapper<ReviewRecord> wrapper = new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getResult, resultFilter)
                .orderByAsc(ReviewRecord::getCreatedAt);

        Page<ReviewRecord> result = reviewRecordMapper.selectPage(pg, wrapper);
        Page<ReviewVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<ReviewVO> voList = result.getRecords().stream().map(r -> {
            ReviewVO vo = new ReviewVO();
            vo.setId(r.getId());
            vo.setDocumentId(r.getDocumentId());
            vo.setReviewerId(r.getReviewerId());
            vo.setResult(r.getResult());
            vo.setComment(r.getComment());
            vo.setReviewedAt(r.getReviewedAt());
            vo.setCreatedAt(r.getCreatedAt());

            Document doc = documentMapper.selectById(r.getDocumentId());
            if (doc != null) {
                vo.setDocumentName(doc.getFileName());
                vo.setDocumentStatus(doc.getStatus());
                vo.setChunkCount(doc.getChunkCount());
            }
            return vo;
        }).toList();
        voPage.setRecords(voList);
        return Result.success(voPage);
    }

    @Override
    @Transactional
    public Result<Void> submitReview(ReviewSubmitDTO dto, Long reviewerId) {
        Document doc = documentMapper.selectById(dto.getDocumentId());
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }

        // --- 分支1: 块审核（CHUNK_REVIEW → EMBEDDING 或重新分块）---
        if (DocumentStatus.CHUNK_REVIEW.name().equals(doc.getStatus())) {
            ReviewRecord record = reviewRecordMapper.selectOne(
                    new LambdaQueryWrapper<ReviewRecord>()
                            .eq(ReviewRecord::getDocumentId, dto.getDocumentId())
                            .eq(ReviewRecord::getResult, ReviewResult.PENDING.name()));
            if (record != null) {
                record.setReviewerId(reviewerId);
                record.setResult(dto.getResult());
                record.setComment(dto.getComment());
                record.setReviewedAt(LocalDateTime.now());
                reviewRecordMapper.updateById(record);
            }
            if ("APPROVED".equals(dto.getResult())) {
                stateMachine.transit(doc, DocumentStatus.EMBEDDING.name());
                if (doc.getChunkCount() != null && doc.getChunkCount() > 0) {
                    sendEmbedProcessMessage(doc);
                }
            } else {
                stateMachine.transit(doc, DocumentStatus.CHUNKING.name());
                sendChunkProcessMessage(doc);
            }
            return Result.success();
        }

        // --- 分支2: 内容审核 ---
        ReviewRecord record = reviewRecordMapper.selectOne(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getDocumentId, dto.getDocumentId())
                        .eq(ReviewRecord::getResult, ReviewResult.PENDING.name()));

        if (record == null) {
            throw new BusinessException(ResultCodeEnum.REVIEW_ALREADY_HANDLED);
        }

        record.setReviewerId(reviewerId);
        record.setResult(dto.getResult());
        record.setComment(dto.getComment());
        record.setReviewedAt(LocalDateTime.now());
        reviewRecordMapper.updateById(record);

        // 仅当文档处于可审核状态时才触发状态转移，终态/已处理状态仅更新审核记录
        String docStatus = doc.getStatus();
        if (!DocumentStatus.PENDING_REVIEW.name().equals(docStatus)
                && !DocumentStatus.CHUNK_REVIEW.name().equals(docStatus)) {
            log.warn("文档状态不是待审核/待块审核，仅更新审核记录: docId={}, status={}", doc.getId(), docStatus);
            return Result.success();
        }

        if ("APPROVED".equals(dto.getResult())) {
            // 如果文档已有分块(预分块完成)，直接向量化；否则走旧流程先分块
            if (doc.getChunkCount() != null && doc.getChunkCount() > 0) {
                stateMachine.transit(doc, DocumentStatus.EMBEDDING.name());
                sendEmbedProcessMessage(doc);
            } else {
                stateMachine.transit(doc, DocumentStatus.APPROVED.name());
                stateMachine.transit(doc, DocumentStatus.CHUNKING.name());
                sendChunkProcessMessage(doc);
            }
        } else {
            stateMachine.transit(doc, DocumentStatus.REJECTED.name());
        }

        return Result.success();
    }

    private void sendChunkProcessMessage(Document doc) {
        String taskId = "task-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-chunk-" + doc.getId();
        KafkaMessage message = new KafkaMessage();
        message.setTaskId(taskId);
        message.setTaskType(TaskType.CHUNK_PROCESS.name());
        message.setDocumentId(doc.getId());
        message.setKbId(doc.getKbId());
        // Construct cleaned path from the FILE_PROCESS convention
        String originalPath = doc.getMinioPath();
        String cleanedPath = originalPath != null
                ? minioConfig.getBucketName() + "/" + buildCleanedPath(originalPath)
                : "";
        message.setData(JSONUtil.createObj()
                .set("cleanedPath", cleanedPath)
                .set("fileName", doc.getFileName())
                .set("chunkStrategy", doc.getChunkStrategy() != null ? doc.getChunkStrategy() : "semantic")
                .set("chunkConfig", doc.getChunkConfig()));
        message.setCreatedAt(LocalDateTime.now().toString());

        kafkaTemplate.send(KafkaConstants.TOPIC_CHUNK_PROCESS, taskId, JSONUtil.toJsonStr(message));
        log.info("CHUNK_PROCESS消息已发送: taskId={}, docId={}", taskId, doc.getId());
    }

    private void sendEmbedProcessMessage(Document doc) {
        String taskId = "task-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-embed-" + doc.getId();

        // Fetch chunks from database
        List<DocumentChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, doc.getId())
                        .eq(DocumentChunk::getStatus, "ACTIVE"));

        List<cn.hutool.json.JSONObject> chunkList = new java.util.ArrayList<>();
        for (DocumentChunk c : chunks) {
            chunkList.add(JSONUtil.createObj()
                    .set("chunk_id", c.getChunkId())
                    .set("chunk_index", c.getChunkIndex())
                    .set("content", c.getContent()));
        }

        KafkaMessage message = new KafkaMessage();
        message.setTaskId(taskId);
        message.setTaskType(TaskType.EMBED_PROCESS.name());
        message.setDocumentId(doc.getId());
        message.setKbId(doc.getKbId());
        message.setData(JSONUtil.createObj()
                .set("fileName", doc.getFileName())
                .set("chunks", chunkList));
        message.setCreatedAt(LocalDateTime.now().toString());

        kafkaTemplate.send(KafkaConstants.TOPIC_EMBED_PROCESS, taskId, JSONUtil.toJsonStr(message));
        log.info("EMBED_PROCESS消息已发送: taskId={}, docId={}, chunks={}", taskId, doc.getId(), chunkList.size());
    }

    @Override
    @Transactional
    public Result<Void> batchApprove(Long[] documentIds, Long reviewerId) {
        for (Long docId : documentIds) {
            ReviewSubmitDTO dto = new ReviewSubmitDTO();
            dto.setDocumentId(docId);
            dto.setResult("APPROVED");
            dto.setComment("批量审核通过");
            submitReview(dto, reviewerId);
        }
        return Result.success();
    }

    /**
     * 定时任务：每5分钟检查超时未审核的记录，自动通过。
     * 同时处理内容审核（PENDING_REVIEW）和块审核（CHUNK_REVIEW）两种场景。
     */
    @Override
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void autoApproveTimeout() {
        // 读取审核超时配置
        int hours = 24;
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, "review.auto_approve_hours"));
        if (config != null) {
            hours = Integer.parseInt(config.getConfigValue());
        }
        final int autoApproveHours = hours;

        LocalDateTime threshold = LocalDateTime.now().minusHours(autoApproveHours);

        // --- 内容审核超时（PENDING_REVIEW → APPROVED → CHUNKING）---
        List<ReviewRecord> timeoutRecords = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getResult, ReviewResult.PENDING.name())
                        .lt(ReviewRecord::getCreatedAt, threshold));

        for (ReviewRecord record : timeoutRecords) {
            int finalHours = autoApproveHours;
            redisLockUtil.executeWithLock("review:auto:" + record.getId(), () -> {
                ReviewRecord fresh = reviewRecordMapper.selectById(record.getId());
                if (fresh == null || !ReviewResult.PENDING.name().equals(fresh.getResult())) {
                    return;
                }

                Document doc = documentMapper.selectById(record.getDocumentId());
                if (doc == null) return;

                fresh.setResult(ReviewResult.APPROVED.name());
                fresh.setAutoApproved(1);
                fresh.setComment("[系统] 超过" + finalHours + "小时未审核，自动通过");
                fresh.setReviewedAt(LocalDateTime.now());
                reviewRecordMapper.updateById(fresh);

                // 已有分块→直接向量化；无分块→走旧流程先分块
                if (doc.getChunkCount() != null && doc.getChunkCount() > 0) {
                    stateMachine.transit(doc, DocumentStatus.EMBEDDING.name());
                    sendEmbedProcessMessage(doc);
                } else {
                    stateMachine.transit(doc, DocumentStatus.APPROVED.name());
                    stateMachine.transit(doc, DocumentStatus.CHUNKING.name());
                    sendChunkProcessMessage(doc);
                }
                log.info("超时自动审核通过(内容): docId={}", doc.getId());
            });
        }

        // --- 块审核超时（CHUNK_REVIEW → EMBEDDING）---
        List<Document> chunkReviewDocs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getStatus, DocumentStatus.CHUNK_REVIEW.name())
                        .lt(Document::getUpdatedAt, threshold));

        for (Document doc : chunkReviewDocs) {
            redisLockUtil.executeWithLock("review:auto:chunk:" + doc.getId(), () -> {
                Document fresh = documentMapper.selectById(doc.getId());
                if (fresh == null || !DocumentStatus.CHUNK_REVIEW.name().equals(fresh.getStatus())) {
                    return;
                }

                stateMachine.transit(fresh, DocumentStatus.EMBEDDING.name());
                if (fresh.getChunkCount() != null && fresh.getChunkCount() > 0) {
                    sendEmbedProcessMessage(fresh);
                }
                // 更新对应 CHUNK_REVIEW 的待审核记录
                ReviewRecord chunkRecord = reviewRecordMapper.selectOne(
                        new LambdaQueryWrapper<ReviewRecord>()
                                .eq(ReviewRecord::getDocumentId, fresh.getId())
                                .eq(ReviewRecord::getResult, ReviewResult.PENDING.name()));
                if (chunkRecord != null) {
                    chunkRecord.setResult(ReviewResult.APPROVED.name());
                    chunkRecord.setAutoApproved(1);
                    chunkRecord.setComment("[系统] 超过" + autoApproveHours + "小时未审核，自动通过(块审核)");
                    chunkRecord.setReviewedAt(LocalDateTime.now());
                    reviewRecordMapper.updateById(chunkRecord);
                }
                log.info("超时自动审核通过(块审核): docId={}", fresh.getId());
            });
        }
    }

    private String buildCleanedPath(String minioPath) {
        if (minioPath == null) return "";
        int lastDot = minioPath.lastIndexOf('.');
        if (lastDot > 0) {
            return minioPath.substring(0, lastDot) + "_cleaned.md";
        }
        return minioPath + "_cleaned.md";
    }
}
