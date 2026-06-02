package com.rag.controller.api;
import org.springframework.context.annotation.Profile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.domain.entity.Feedback;
import com.rag.service.feedback.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "反馈管理", description = "用户反馈提交与处理")
@Profile("qa")
@Profile("qa")
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "提交反馈")
    @PostMapping
    public Result<Void> submit(@RequestBody Feedback feedback) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return feedbackService.submit(feedback, userId);
    }

    @Operation(summary = "反馈列表")
    @GetMapping
    @PreAuthorize("hasAuthority('FEEDBACK:VIEW')")
    public Result<Page<Feedback>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String status) {
        return feedbackService.list(page, size, status);
    }

    @Operation(summary = "反馈详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FEEDBACK:VIEW')")
    public Result<Feedback> detail(@PathVariable Long id) {
        return feedbackService.detail(id);
    }

    @Operation(summary = "处理反馈")
    @PutMapping("/{id}/handle")
    @PreAuthorize("hasAuthority('FEEDBACK:HANDLE')")
    public Result<Void> handle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long handlerId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return feedbackService.handle(id, body.get("handlerNote"), handlerId);
    }
}
