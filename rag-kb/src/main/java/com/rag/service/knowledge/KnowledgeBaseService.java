package com.rag.service.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.service.knowledge.dto.KnowledgeBaseQueryDTO;
import com.rag.service.knowledge.dto.KnowledgeBaseSaveDTO;
import com.rag.service.knowledge.dto.KnowledgeBaseVO;

public interface KnowledgeBaseService {

    Result<KnowledgeBaseVO> create(KnowledgeBaseSaveDTO dto, Long userId);

    Result<KnowledgeBaseVO> update(Long id, KnowledgeBaseSaveDTO dto);

    Result<Void> delete(Long id);

    Result<KnowledgeBaseVO> getById(Long id);

    Result<Page<KnowledgeBaseVO>> list(KnowledgeBaseQueryDTO query);

    Result<Void> updateDocumentStatus(Long kbId, Long docId, Boolean enabled);
}
