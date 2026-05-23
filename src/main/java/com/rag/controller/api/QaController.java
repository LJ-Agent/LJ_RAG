package com.rag.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.annotation.RateLimit;
import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.service.qa.QaService;
import com.rag.service.qa.dto.AnswerVO;
import com.rag.service.qa.dto.ChatHistoryVO;
import com.rag.service.qa.dto.QuestionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "问答服务", description = "知识库问答、流式输出、问答历史")
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    @Operation(summary = "非流式问答")
    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('QA:ASK')")
    @RateLimit(key = "qa", permitsPerSecond = 10, message = "提问过于频繁，请稍后再试")
    public Result<AnswerVO> chat(@Valid @RequestBody QuestionDTO dto) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return qaService.chat(dto, userId);
    }

    @Operation(summary = "流式问答（SSE）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('QA:ASK')")
    @RateLimit(key = "qa_stream", permitsPerSecond = 5, message = "提问过于频繁，请稍后再试")
    public SseEmitter streamChat(@Valid @RequestBody QuestionDTO dto) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return qaService.streamChat(dto, userId);
    }

    @Operation(summary = "问答历史")
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('QA:HISTORY')")
    public Result<Page<ChatHistoryVO>> getHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return qaService.getHistory(userId, page, size);
    }
}
