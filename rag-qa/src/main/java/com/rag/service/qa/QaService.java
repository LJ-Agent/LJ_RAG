package com.rag.service.qa;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.qa.dto.AnswerVO;
import com.rag.service.qa.dto.ChatHistoryVO;
import com.rag.service.qa.dto.QuestionDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface QaService {

    Result<AnswerVO> chat(QuestionDTO dto, Long userId);

    SseEmitter streamChat(QuestionDTO dto, Long userId);

    Result<Page<ChatHistoryVO>> getHistory(Long userId, Integer page, Integer size);
}
