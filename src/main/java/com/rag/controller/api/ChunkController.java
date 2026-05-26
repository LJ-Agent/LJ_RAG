package com.rag.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.chunk.ChunkService;
import com.rag.service.chunk.dto.ChunkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "分块管理", description = "文档块浏览、编辑、审核、向量化")
@RestController
@RequestMapping("/api/chunks")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkService chunkService;

    @Operation(summary = "获取文档块列表")
    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<Page<ChunkVO>> list(
            @RequestParam Long documentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            return chunkService.searchChunks(documentId, keyword, page, size);
        }
        return chunkService.listByDocumentId(documentId, page, size);
    }

    @Operation(summary = "新增块")
    @PostMapping
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<ChunkVO> create(@RequestBody Map<String, Object> body) {
        Long documentId = body.get("documentId") != null
                ? ((Number) body.get("documentId")).longValue() : null;
        String content = body.get("content") != null ? body.get("content").toString() : null;
        return chunkService.createChunk(documentId, content);
    }

    @Operation(summary = "按业务chunkId查询单个块")
    @GetMapping("/by-chunk-id/{chunkId}")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<ChunkVO> getByChunkId(@PathVariable String chunkId) {
        return chunkService.getByChunkId(chunkId);
    }

    @Operation(summary = "编辑块内容")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<ChunkVO> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return chunkService.updateChunk(id, body.get("content"));
    }

    @Operation(summary = "删除块")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<Void> delete(@PathVariable Long id) {
        return chunkService.deleteChunk(id);
    }

    @Operation(summary = "批量修改块状态")
    @PutMapping("/batch-status")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<Void> batchSetStatus(@RequestBody Map<String, Object> body) {
        Object rawIds = body.get("ids");
        if (!(rawIds instanceof List<?> rawList)) {
            throw new com.rag.common.exception.BusinessException(
                    com.rag.common.result.ResultCodeEnum.PARAM_ERROR.getCode(), "ids参数必须为数组");
        }
        List<Long> ids = rawList.stream()
                .filter(java.util.Objects::nonNull)
                .map(o -> o instanceof Number n ? n.longValue() : Long.valueOf(o.toString()))
                .toList();
        Object rawStatus = body.get("status");
        if (rawStatus == null) {
            throw new com.rag.common.exception.BusinessException(
                    com.rag.common.result.ResultCodeEnum.PARAM_ERROR.getCode(), "status参数不能为空");
        }
        String status = rawStatus.toString();
        return chunkService.batchSetStatus(ids, status);
    }

    @Operation(summary = "获取块统计")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<ChunkVO.ChunkStats> stats(@RequestParam Long documentId) {
        return chunkService.getStats(documentId);
    }

    @Operation(summary = "发起向量化入库")
    @PostMapping("/start-embedding")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<Void> startEmbedding(@RequestParam Long documentId) {
        return chunkService.startEmbedding(documentId);
    }

    @Operation(summary = "同步文档分块计数")
    @PostMapping("/sync-count")
    @PreAuthorize("hasAuthority('DOCUMENT:VIEW')")
    public Result<Integer> syncChunkCount(@RequestParam Long documentId) {
        return chunkService.syncChunkCount(documentId);
    }
}
