package com.rag.service.chunk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.chunk.dto.ChunkVO;

import java.util.List;

public interface ChunkService {

    Result<Page<ChunkVO>> listByDocumentId(Long documentId, Integer page, Integer size);

    Result<ChunkVO> updateChunk(Long id, String content);

    Result<Void> deleteChunk(Long id);

    Result<Void> batchSetStatus(List<Long> ids, String status);

    Result<ChunkVO.ChunkStats> getStats(Long documentId);

    Result<Void> startEmbedding(Long documentId);
}
