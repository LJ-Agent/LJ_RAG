package com.rag.controller.api;
import org.springframework.context.annotation.Profile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.service.review.ReviewService;
import com.rag.service.review.dto.ReviewSubmitDTO;
import com.rag.service.review.dto.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "审核管理", description = "文档审核列表、提交审核")
@Profile("doc")
@Profile("doc")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "获取审核列表（可按结果过滤）")
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('REVIEW:VIEW')")
    public Result<Page<ReviewVO>> getPendingList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String result) {
        return reviewService.getPendingList(page, size, result);
    }

    @Operation(summary = "获取待块审核列表（CHUNK_REVIEW 状态的文档）")
    @GetMapping("/chunk-review")
    @PreAuthorize("hasAuthority('REVIEW:VIEW')")
    public Result<Page<ReviewVO>> getChunkReviewList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return reviewService.getChunkReviewList(page, size);
    }

    @Operation(summary = "提交审核结果")
    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('REVIEW:APPROVE')")
    public Result<Void> submitReview(@Valid @RequestBody ReviewSubmitDTO dto) {
        Long reviewerId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return reviewService.submitReview(dto, reviewerId);
    }

    @Operation(summary = "批量审核通过")
    @PostMapping("/batch-approve")
    @PreAuthorize("hasAuthority('REVIEW:APPROVE')")
    public Result<Void> batchApprove(@RequestBody Long[] documentIds) {
        Long reviewerId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return reviewService.batchApprove(documentIds, reviewerId);
    }
}
