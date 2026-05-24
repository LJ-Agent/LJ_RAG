package com.rag.service.qa.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.ChatRecord;
import com.rag.domain.entity.ChatSession;
import com.rag.domain.mapper.ChatRecordMapper;
import com.rag.domain.mapper.ChatSessionMapper;
import com.rag.service.qa.ChatSessionService;
import com.rag.service.qa.dto.ChatHistoryVO;
import com.rag.service.qa.dto.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatRecordMapper chatRecordMapper;

    @Override
    public Result<Page<ChatSessionVO>> listSessions(Long userId, Integer page, Integer size) {
        Page<ChatSession> pg = new Page<>(page, size);
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getDeleted, 0)
                .orderByDesc(ChatSession::getUpdatedAt);

        Page<ChatSession> result = sessionMapper.selectPage(pg, wrapper);
        Page<ChatSessionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(s -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setId(s.getId());
            vo.setUserId(s.getUserId());
            vo.setTitle(s.getTitle());
            vo.setKbIds(s.getKbIds());
            vo.setMessageCount(s.getMessageCount());
            vo.setCreatedAt(s.getCreatedAt());
            vo.setUpdatedAt(s.getUpdatedAt());
            return vo;
        }).toList());
        return Result.success(voPage);
    }

    @Override
    public Result<ChatSessionVO> createSession(Long userId, String kbIds, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setKbIds(kbIds);
        session.setTitle(title != null ? title : "新会话");
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setDeleted(0);
        sessionMapper.insert(session);

        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setTitle(session.getTitle());
        vo.setKbIds(session.getKbIds());
        vo.setMessageCount(0);
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return Result.success(vo);
    }

    @Override
    public Result<Void> updateTitle(Long sessionId, Long userId, String title) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "会话不存在");
        }
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> deleteSession(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "会话不存在");
        }
        session.setDeleted(1);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        // 软删除关联的问答记录
        LambdaUpdateWrapper<ChatRecord> recordWrapper = new LambdaUpdateWrapper<ChatRecord>()
                .eq(ChatRecord::getSessionId, sessionId);
        chatRecordMapper.delete(recordWrapper);

        log.info("会话已删除: sessionId={}, userId={}", sessionId, userId);
        return Result.success();
    }

    @Override
    public Result<Page<ChatHistoryVO>> getSessionRecords(Long sessionId, Long userId, Integer page, Integer size) {
        // 验证会话属于当前用户
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getDeleted() == 1) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "会话不存在");
        }

        Page<ChatRecord> pg = new Page<>(page, size);
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<ChatRecord>()
                .eq(ChatRecord::getSessionId, sessionId)
                .orderByAsc(ChatRecord::getCreatedAt);

        Page<ChatRecord> result = chatRecordMapper.selectPage(pg, wrapper);
        Page<ChatHistoryVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(r -> {
            ChatHistoryVO vo = new ChatHistoryVO();
            vo.setId(r.getId());
            vo.setQuestion(r.getQuestion());
            vo.setAnswer(r.getAnswer());
            vo.setRating(r.getRating());
            vo.setLatencyMs(r.getLatencyMs());
            vo.setIsStream(r.getIsStream());
            vo.setCreatedAt(r.getCreatedAt());
            return vo;
        }).toList());
        return Result.success(voPage);
    }
}
