package com.rag.service.chunk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.domain.entity.DocumentChunk;
import com.rag.domain.mapper.DocumentChunkMapper;
import com.rag.service.chunk.dto.ChunkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChunkService {

    private final DocumentChunkMapper mapper;

    private ChunkVO toVO(DocumentChunk e) {
        ChunkVO vo = new ChunkVO();
        vo.setId(e.getId());
        vo.setDocumentId(e.getDocumentId());
        vo.setChunkId(e.getChunkId());
        vo.setChunkIndex(e.getChunkIndex());
        vo.setContent(e.getContent());
        vo.setLevel(e.getLevel());
        vo.setParentId(e.getParentId());
        vo.setCharCount(e.getCharCount());
        vo.setStatus(e.getStatus());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public Result<Page<ChunkVO>> listByDocumentId(Long documentId, int page, int size) {
        Page<DocumentChunk> pg = new Page<>(page, size);
        Page<DocumentChunk> result = mapper.selectPage(pg,
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, documentId)
                        .orderByAsc(DocumentChunk::getChunkIndex));
        Page<ChunkVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return Result.success(voPage);
    }

    public Result<Page<ChunkVO>> searchChunks(Long documentId, String keyword, int page, int size) {
        Page<DocumentChunk> pg = new Page<>(page, size);
        Page<DocumentChunk> result = mapper.selectPage(pg,
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, documentId)
                        .like(DocumentChunk::getContent, keyword)
                        .orderByAsc(DocumentChunk::getChunkIndex));
        Page<ChunkVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return Result.success(voPage);
    }

    public Result<ChunkVO> createChunk(Long documentId, String content) {
        DocumentChunk e = new DocumentChunk();
        e.setDocumentId(documentId);
        e.setContent(content);
        e.setChunkIndex(0);
        e.setStatus("active");
        mapper.insert(e);
        return Result.success(toVO(e));
    }

    public Result<ChunkVO> getByChunkId(String chunkId) {
        DocumentChunk e = mapper.selectOne(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getChunkId, chunkId));
        return e != null ? Result.success(toVO(e)) : Result.fail(404, "块不存在");
    }

    public Result<ChunkVO> updateChunk(Long id, String content) {
        DocumentChunk e = mapper.selectById(id);
        if (e == null) return Result.fail(404, "块不存在");
        e.setContent(content);
        mapper.updateById(e);
        return Result.success(toVO(e));
    }

    public Result<Void> deleteChunk(Long id) {
        mapper.deleteById(id);
        return Result.success();
    }

    public Result<Void> batchSetStatus(List<Long> ids, String status) {
        for (Long id : ids) {
            DocumentChunk e = mapper.selectById(id);
            if (e != null) {
                e.setStatus(status);
                mapper.updateById(e);
            }
        }
        return Result.success();
    }

    public Result<ChunkVO.ChunkStats> getStats(Long documentId) {
        List<DocumentChunk> all = mapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
        ChunkVO.ChunkStats stats = new ChunkVO.ChunkStats();
        stats.setTotalCount(all.size());
        stats.setActiveCount(all.stream().filter(c -> "active".equals(c.getStatus())).count());
        stats.setDeletedCount(all.stream().filter(c -> "deleted".equals(c.getStatus())).count());
        stats.setTotalChars(all.stream().mapToLong(c -> c.getCharCount() != null ? c.getCharCount() : 0).sum());
        return Result.success(stats);
    }

    public Result<Void> startEmbedding(Long documentId) {
        // 标记为 embedding 状态，实际由异步任务处理
        List<DocumentChunk> chunks = mapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
        for (DocumentChunk c : chunks) {
            c.setStatus("embedding");
            mapper.updateById(c);
        }
        return Result.success();
    }

    public Result<Integer> syncChunkCount(Long documentId) {
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
        return Result.success(count.intValue());
    }

    public Result<Void> submitForReview(Long documentId) {
        List<DocumentChunk> chunks = mapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, documentId));
        for (DocumentChunk c : chunks) {
            c.setStatus("pending_review");
            mapper.updateById(c);
        }
        return Result.success();
    }
}
