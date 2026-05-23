package com.rag.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.service.knowledge.KnowledgeBaseService;
import com.rag.service.knowledge.dto.KnowledgeBaseQueryDTO;
import com.rag.service.knowledge.dto.KnowledgeBaseSaveDTO;
import com.rag.service.knowledge.dto.KnowledgeBaseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "知识库管理", description = "知识库CURD、文档关联、上下架")
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @Operation(summary = "创建知识库")
    @PostMapping
    @PreAuthorize("hasAuthority('KB:CREATE')")
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseSaveDTO dto) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return kbService.create(dto, userId);
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('KB:UPDATE')")
    public Result<KnowledgeBaseVO> update(@PathVariable Long id,
                                           @Valid @RequestBody KnowledgeBaseSaveDTO dto) {
        return kbService.update(id, dto);
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('KB:DELETE')")
    public Result<Void> delete(@PathVariable Long id) {
        return kbService.delete(id);
    }

    @Operation(summary = "获取知识库详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('KB:VIEW')")
    public Result<KnowledgeBaseVO> getById(@PathVariable Long id) {
        return kbService.getById(id);
    }

    @Operation(summary = "查询知识库列表")
    @GetMapping
    @PreAuthorize("hasAuthority('KB:VIEW')")
    public Result<Page<KnowledgeBaseVO>> list(KnowledgeBaseQueryDTO query) {
        return kbService.list(query);
    }

    @Operation(summary = "文档上下架")
    @PutMapping("/{kbId}/documents/{docId}")
    @PreAuthorize("hasAuthority('KB:UPDATE')")
    public Result<Void> updateDocumentStatus(@PathVariable Long kbId,
                                              @PathVariable Long docId,
                                              @RequestParam Boolean enabled) {
        return kbService.updateDocumentStatus(kbId, docId, enabled);
    }
}
