package com.rag.service.statemachine;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rag.common.enums.DocumentStatus;
import com.rag.common.enums.ReviewResult;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.ReviewRecord;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.domain.mapper.ReviewRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 文档状态机，保证状态转移的原子性和一致性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStateMachine {

    private final DocumentMapper documentMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final StateTransitionValidator validator = new StateTransitionValidator();

    @Transactional
    public void transit(Document document, String targetStatus) {
        DocumentStatus from = DocumentStatus.valueOf(document.getStatus());
        DocumentStatus to = DocumentStatus.valueOf(targetStatus);

        validator.validate(from, to);

        // 乐观锁更新：仅当当前状态与读取时一致才更新
        LambdaUpdateWrapper<Document> updateWrapper = new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, document.getId())
                .eq(Document::getStatus, document.getStatus())
                .set(Document::getStatus, to.name());
        if (to == DocumentStatus.COMPLETED) {
            updateWrapper.set(Document::getCompletedAt, LocalDateTime.now());
        }
        int updated = documentMapper.update(null, updateWrapper);

        if (updated == 0) {
            // 重新读取最新状态
            Document fresh = Optional.ofNullable(documentMapper.selectById(document.getId()))
                    .orElseThrow(() -> new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND));
            throw new BusinessException(ResultCodeEnum.DOCUMENT_STATUS_ERROR.getCode(),
                    String.format("状态转移失败: %s -> %s, 当前状态: %s",
                            document.getStatus(), targetStatus, fresh.getStatus()));
        }

        document.setStatus(to.name());
        if (to == DocumentStatus.COMPLETED) {
            document.setCompletedAt(LocalDateTime.now());
        }

        // Auto-create review record only when entering PENDING_REVIEW (not CHUNK_REVIEW — chunk edit phase)
        if (to == DocumentStatus.PENDING_REVIEW) {
            ReviewRecord record = new ReviewRecord();
            record.setDocumentId(document.getId());
            record.setResult(ReviewResult.PENDING.name());
            reviewRecordMapper.insert(record);
            log.info("审核记录已创建: docId={}, status={}", document.getId(), to.name());
        }

        log.info("状态转移成功: docId={}, {} -> {}", document.getId(), from.name(), to.name());
    }

    @Transactional
    public void fail(Document document, DocumentStatus failedStatus, String errorMessage) {
        document.setStatus(failedStatus.name());
        document.setErrorMessage(errorMessage);
        documentMapper.updateById(document);
        log.warn("文档处理失败: docId={}, status={}, error={}",
                document.getId(), failedStatus.name(), errorMessage);
    }

    @Transactional
    public void transitToNext(Document document) {
        DocumentStatus current = DocumentStatus.valueOf(document.getStatus());
        DocumentStatus next = DocumentStatus.nextAfterTaskComplete(current);
        if (next == null) {
            log.info("文档已处于终态或无需流转: docId={}, status={}", document.getId(), current);
            return;
        }
        transit(document, next.name());
    }
}
