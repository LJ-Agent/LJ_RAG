import org.springframework.context.annotation.Profile;
package com.rag.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.annotation.RateLimit;
import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.service.qa.ChatSessionService;
import com.rag.service.qa.QaService;
import com.rag.service.qa.dto.AnswerVO;
import com.rag.service.qa.dto.ChatHistoryVO;
import com.rag.service.qa.dto.ChatSessionVO;
import com.rag.service.qa.dto.QuestionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "问答服务", description = "知识库问答、流式输出、问答历史")
@Profile("qa")
@Profile("qa")
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final ChatSessionService sessionService;

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
    @PostMapping("/history")
    @PreAuthorize("hasAuthority('QA:HISTORY')")
    public Result<Page<ChatHistoryVO>> getHistory(@RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.containsKey("page") ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.containsKey("size") ? ((Number) body.get("size")).intValue() : 20;
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return qaService.getHistory(userId, page, size);
    }

    // --- 会话管理 ---

    @Operation(summary = "获取会话列表")
    @PostMapping("/sessions/list")
    public Result<Page<ChatSessionVO>> listSessions(@RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.containsKey("page") ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.containsKey("size") ? ((Number) body.get("size")).intValue() : 50;
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return sessionService.listSessions(userId, page, size);
    }

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(@RequestBody ChatSessionVO body) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return sessionService.createSession(userId, body.getKbIds(), body.getTitle());
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/sessions/{id}")
    public Result<Void> updateSession(@PathVariable Long id, @RequestBody ChatSessionVO body) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return sessionService.updateTitle(id, userId, body.getTitle());
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return sessionService.deleteSession(id, userId);
    }

    @Operation(summary = "批量删除会话")
    @PostMapping("/sessions/batch-delete")
    public Result<Void> batchDeleteSessions(@RequestBody Long[] ids) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return sessionService.batchDeleteSessions(ids, userId);
    }

    @Operation(summary = "获取会话的问答记录")
    @PostMapping("/sessions/{id}/records")
    public Result<Page<ChatHistoryVO>> getSessionRecords(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.containsKey("page") ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.containsKey("size") ? ((Number) body.get("size")).intValue() : 50;
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return sessionService.getSessionRecords(id, userId, page, size);
    }
}
