package com.rag.service.feedback.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.Feedback;
import com.rag.domain.mapper.FeedbackMapper;
import com.rag.service.feedback.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Override
    public Result<Void> submit(Feedback feedback, Long userId) {
        feedback.setUserId(userId);
        feedback.setStatus("PENDING");
        feedbackMapper.insert(feedback);
        return Result.success();
    }

    @Override
    public Result<Page<Feedback>> list(Integer page, Integer size, String status) {
        Page<Feedback> pg = new Page<>(page, size);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Feedback::getStatus, status);
        }
        wrapper.orderByDesc(Feedback::getCreatedAt);
        return Result.success(feedbackMapper.selectPage(pg, wrapper));
    }

    @Override
    public Result<Void> handle(Long id, String handlerNote, Long handlerId) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        feedback.setStatus("RESOLVED");
        feedback.setHandlerNote(handlerNote);
        feedback.setHandledBy(handlerId);
        feedback.setHandledAt(LocalDateTime.now());
        feedbackMapper.updateById(feedback);
        return Result.success();
    }

    @Override
    public Result<Feedback> detail(Long id) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        return Result.success(feedback);
    }
}
