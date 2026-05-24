package com.rag.service.chunk.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.constant.KafkaConstants;
import com.rag.common.enums.DocumentStatus;
import com.rag.common.enums.TaskType;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.communication.kafka.dto.KafkaMessage;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.DocumentChunk;
import com.rag.domain.mapper.DocumentChunkMapper;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.service.chunk.ChunkService;
import com.rag.service.chunk.dto.ChunkVO;
import com.rag.service.statemachine.DocumentStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkServiceImpl implements ChunkService {

    private final DocumentChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;
    private final DocumentStateMachine stateMachine;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public Result<Page<ChunkVO>> listByDocumentId(Long documentId, Integer page, Integer size) {
        Page<DocumentChunk> pg = new Page<>(page, size);
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkIndex);

        Page<DocumentChunk> result = chunkMapper.selectPage(pg, wrapper);
        Page<ChunkVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return Result.success(voPage);
    }

    @Override
    public Result<ChunkVO> updateChunk(Long id, String content) {
        DocumentChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "块不存在");
        }
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunkMapper.updateById(chunk);
        return Result.success(toVO(chunk));
    }

    @Override
    public Result<Void> deleteChunk(Long id) {
        DocumentChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "块不存在");
        }
        chunk.setStatus("DELETED");
        chunkMapper.updateById(chunk);
        return Result.success();
    }

    @Override
    public Result<Void> batchSetStatus(List<Long> ids, String status) {
        for (Long id : ids) {
            DocumentChunk chunk = chunkMapper.selectById(id);
            if (chunk != null) {
                chunk.setStatus(status);
                chunkMapper.updateById(chunk);
            }
        }
        return Result.success();
    }

    @Override
    public Result<ChunkVO> getByChunkId(String chunkId) {
        DocumentChunk chunk = chunkMapper.selectOne(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getChunkId, chunkId));
        if (chunk == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "块不存在");
        }
        return Result.success(toVO(chunk));
    }

    @Override
    public Result<ChunkVO.ChunkStats> getStats(Long documentId) {
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId);
        List<DocumentChunk> all = chunkMapper.selectList(wrapper);

        ChunkVO.ChunkStats stats = new ChunkVO.ChunkStats();
        stats.setTotalCount(all.size());
        stats.setActiveCount((int) all.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count());
        stats.setDeletedCount((int) all.stream().filter(c -> "DELETED".equals(c.getStatus())).count());
        stats.setTotalChars(all.stream().filter(c -> "ACTIVE".equals(c.getStatus()))
                .mapToLong(c -> c.getCharCount() != null ? c.getCharCount() : 0).sum());
        return Result.success(stats);
    }

    @Override
    @Transactional
    public Result<Void> startEmbedding(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }
        DocumentStatus current = DocumentStatus.valueOf(doc.getStatus());
        if (current != DocumentStatus.CHUNK_REVIEW) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_STATUS_ERROR.getCode(),
                    "当前文档状态不允许发起向量化: " + current.getDescription());
        }

        // 获取所有活跃块
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .eq(DocumentChunk::getStatus, "ACTIVE");
        List<DocumentChunk> activeChunks = chunkMapper.selectList(wrapper);

        if (activeChunks.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "没有可向量化的块");
        }

        // 转移到 EMBEDDING
        stateMachine.transit(doc, DocumentStatus.EMBEDDING.name());

        // 发送 EMBED_PROCESS Kafka 消息
        String taskId = "task-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-embed-" + doc.getId();
        KafkaMessage message = new KafkaMessage();
        message.setTaskId(taskId);
        message.setTaskType(TaskType.EMBED_PROCESS.name());
        message.setDocumentId(doc.getId());
        message.setKbId(doc.getKbId());
        message.setData(JSONUtil.createObj()
                .set("chunks", activeChunks.stream().map(c -> JSONUtil.createObj()
                        .set("chunkId", c.getChunkId())
                        .set("content", c.getContent())
                        .set("chunkIndex", c.getChunkIndex())
                        .set("documentId", c.getDocumentId())
                        .set("kbId", doc.getKbId())
                ).collect(Collectors.toList()))
                .set("fileName", doc.getFileName()));
        message.setCreatedAt(LocalDateTime.now().toString());

        kafkaTemplate.send(KafkaConstants.TOPIC_EMBED_PROCESS, taskId, JSONUtil.toJsonStr(message));
        log.info("EMBED_PROCESS消息已发送: taskId={}, docId={}, chunkCount={}", taskId, doc.getId(), activeChunks.size());
        return Result.success();
    }

    private ChunkVO toVO(DocumentChunk c) {
        ChunkVO vo = new ChunkVO();
        vo.setId(c.getId());
        vo.setDocumentId(c.getDocumentId());
        vo.setChunkId(c.getChunkId());
        vo.setChunkIndex(c.getChunkIndex());
        vo.setContent(c.getContent());
        vo.setLevel(c.getLevel());
        vo.setParentId(c.getParentId());
        vo.setCharCount(c.getCharCount());
        vo.setStatus(c.getStatus());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }
}
