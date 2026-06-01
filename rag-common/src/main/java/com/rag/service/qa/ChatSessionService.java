package com.rag.service.qa;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.qa.dto.ChatHistoryVO;
import com.rag.service.qa.dto.ChatSessionVO;

public interface ChatSessionService {

    Result<Page<ChatSessionVO>> listSessions(Long userId, Integer page, Integer size);

    Result<ChatSessionVO> createSession(Long userId, String kbIds, String title);

    Result<Void> updateTitle(Long sessionId, Long userId, String title);

    Result<Void> deleteSession(Long sessionId, Long userId);

    Result<Void> batchDeleteSessions(Long[] sessionIds, Long userId);

    Result<Page<ChatHistoryVO>> getSessionRecords(Long sessionId, Long userId, Integer page, Integer size);
}
