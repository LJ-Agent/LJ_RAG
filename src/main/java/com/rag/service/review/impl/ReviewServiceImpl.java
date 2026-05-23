package com.rag.service.review.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.enums.DocumentStatus;
import com.rag.common.enums.ReviewResult;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.ReviewRecord;
import com.rag.domain.entity.SystemConfig;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.domain.mapper.ReviewRecordMapper;
import com.rag.domain.mapper.SystemConfigMapper;
import com.rag.infrastructure.lock.RedisLockUtil;
import com.rag.service.review.ReviewService;
import com.rag.service.review.dto.ReviewSubmitDTO;
import com.rag.service.review.dto.ReviewVO;
import com.rag.service.statemachine.DocumentStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRecordMapper reviewRecordMapper;
    private final DocumentMapper documentMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final DocumentStateMachine stateMachine;
    private final RedisLockUtil redisLockUtil;

    @Override
    public Result<Page<ReviewVO>> getPendingList(Integer page, Integer size) {
        Page<ReviewRecord> pg = new Page<>(page, size);
        LambdaQueryWrapper<ReviewRecord> wrapper = new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getResult, ReviewResult.PENDING.name())
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

        // 查找待审核记录
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

        if ("APPROVED".equals(dto.getResult())) {
            stateMachine.transit(doc, DocumentStatus.APPROVED.name());
        } else {
            stateMachine.transit(doc, DocumentStatus.REJECTED.name());
        }

        return Result.success();
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

        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        List<ReviewRecord> timeoutRecords = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .eq(ReviewRecord::getResult, ReviewResult.PENDING.name())
                        .lt(ReviewRecord::getCreatedAt, threshold));

        for (ReviewRecord record : timeoutRecords) {
            int finalHours = hours;
            redisLockUtil.executeWithLock("review:auto:" + record.getId(), () -> {
                // 重新检查，防止并发
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

                stateMachine.transit(doc, DocumentStatus.APPROVED.name());
                log.info("超时自动审核通过: docId={}", doc.getId());
            });
        }
    }
}
