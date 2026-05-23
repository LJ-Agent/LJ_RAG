package com.rag.service.review;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.review.dto.ReviewSubmitDTO;
import com.rag.service.review.dto.ReviewVO;

public interface ReviewService {

    Result<Page<ReviewVO>> getPendingList(Integer page, Integer size);

    Result<Void> submitReview(ReviewSubmitDTO dto, Long reviewerId);

    Result<Void> batchApprove(Long[] documentIds, Long reviewerId);

    void autoApproveTimeout();
}
