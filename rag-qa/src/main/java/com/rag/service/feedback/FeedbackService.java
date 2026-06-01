package com.rag.service.feedback;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.domain.entity.Feedback;

public interface FeedbackService {

    Result<Void> submit(Feedback feedback, Long userId);

    Result<Page<Feedback>> list(Integer page, Integer size, String status);

    Result<Void> handle(Long id, String handlerNote, Long handlerId);

    Result<Feedback> detail(Long id);
}
