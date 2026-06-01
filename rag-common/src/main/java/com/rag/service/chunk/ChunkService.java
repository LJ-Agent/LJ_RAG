package com.rag.service.chunk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.chunk.dto.ChunkVO;

import java.util.List;

public interface ChunkService {

    Result<Page<ChunkVO>> listByDocumentId(Long documentId, Integer page, Integer size);

    Result<Page<ChunkVO>> searchChunks(Long documentId, String keyword, Integer page, Integer size);

    Result<ChunkVO> createChunk(Long documentId, String content);

    Result<ChunkVO> updateChunk(Long id, String content);

    Result<Void> deleteChunk(Long id);

    Result<Void> batchSetStatus(List<Long> ids, String status);

    Result<ChunkVO> getByChunkId(String chunkId);

    Result<ChunkVO.ChunkStats> getStats(Long documentId);

    Result<Void> startEmbedding(Long documentId);

    Result<Integer> syncChunkCount(Long documentId);

    Result<Void> submitForReview(Long documentId);
}
