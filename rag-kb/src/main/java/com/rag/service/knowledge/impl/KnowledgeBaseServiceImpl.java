package com.rag.service.knowledge.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.KnowledgeBase;
import com.rag.domain.entity.User;
import com.rag.common.context.UserContext;
import com.rag.domain.entity.TeamMember;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.domain.mapper.KnowledgeBaseMapper;
import com.rag.domain.mapper.TeamMemberMapper;
import com.rag.domain.mapper.UserMapper;
import com.rag.service.knowledge.KnowledgeBaseService;
import com.rag.service.knowledge.dto.KnowledgeBaseQueryDTO;
import com.rag.service.knowledge.dto.KnowledgeBaseSaveDTO;
import com.rag.service.knowledge.dto.KnowledgeBaseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final UserMapper userMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    @Transactional
    public Result<KnowledgeBaseVO> create(KnowledgeBaseSaveDTO dto, Long userId) {
        Long count = kbMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getKbName, dto.getKbName()));
        if (count > 0) {
            throw new BusinessException(ResultCodeEnum.KB_NAME_DUPLICATE);
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbName(dto.getKbName());
        kb.setDescription(dto.getDescription());
        kb.setCoverUrl(dto.getCoverUrl());
        kb.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        kb.setOwnerId(userId);
        kbMapper.insert(kb);

        return Result.success(toVO(kb));
    }

    @Override
    @Transactional
    public Result<KnowledgeBaseVO> update(Long id, KnowledgeBaseSaveDTO dto) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.KB_NOT_FOUND);
        }

        // 检查名称是否与其他知识库重复
        Long count = kbMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getKbName, dto.getKbName())
                        .ne(KnowledgeBase::getId, id));
        if (count > 0) {
            throw new BusinessException(ResultCodeEnum.KB_NAME_DUPLICATE);
        }

        kb.setKbName(dto.getKbName());
        kb.setDescription(dto.getDescription());
        kb.setCoverUrl(dto.getCoverUrl());
        if (dto.getStatus() != null) {
            kb.setStatus(dto.getStatus());
        }
        kbMapper.updateById(kb);

        return Result.success(toVO(kb));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.KB_NOT_FOUND);
        }

        // 解除关联的文档
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>().eq(Document::getKbId, id));
        for (Document doc : docs) {
            doc.setKbId(null);
            documentMapper.updateById(doc);
        }

        kbMapper.deleteById(id);
        return Result.success();
    }

    @Override
    public Result<KnowledgeBaseVO> getById(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.KB_NOT_FOUND);
        }
        return Result.success(toVO(kb));
    }

    @Override
    public Result<Page<KnowledgeBaseVO>> list(KnowledgeBaseQueryDTO query) {
        Page<KnowledgeBase> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (query.getKbName() != null && !query.getKbName().isEmpty()) {
            wrapper.like(KnowledgeBase::getKbName, query.getKbName());
        }
        if (query.getStatus() != null) {
            wrapper.eq(KnowledgeBase::getStatus, query.getStatus());
        }

        // 团队隔离: 用户仅可见所属团队的KB + 公开KB
        Long userId = UserContext.getUserId();
        boolean isAdmin = UserContext.hasPermission("CONFIG:MANAGE");
        if (!isAdmin) {
            List<TeamMember> memberships = teamMemberMapper.selectList(
                    new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
            List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
            if (teamIds.isEmpty()) {
                // 不属于任何团队 → 只能看公开KB
                wrapper.eq(KnowledgeBase::getVisibility, "public");
            } else {
                wrapper.and(w -> w.in(KnowledgeBase::getTeamId, teamIds)
                        .or().eq(KnowledgeBase::getVisibility, "public"));
            }
        }

        wrapper.orderByDesc(KnowledgeBase::getCreatedAt);

        Page<KnowledgeBase> result = kbMapper.selectPage(page, wrapper);
        Page<KnowledgeBaseVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return Result.success(voPage);
    }

    @Override
    @Transactional
    public Result<Void> updateDocumentStatus(Long kbId, Long docId, Boolean enabled) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null || !kbId.equals(doc.getKbId())) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }
        // 上下架通过文档状态控制
        if (!enabled) {
            doc.setStatus("DISABLED");
        } else {
            doc.setStatus("COMPLETED");
        }
        documentMapper.updateById(doc);
        return Result.success();
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setKbName(kb.getKbName());
        vo.setDescription(kb.getDescription());
        vo.setCoverUrl(kb.getCoverUrl());
        vo.setStatus(kb.getStatus());
        vo.setOwnerId(kb.getOwnerId());
        vo.setTeamId(kb.getTeamId());
        vo.setCreatedAt(kb.getCreatedAt());
        vo.setUpdatedAt(kb.getUpdatedAt());

        // 查询文档数量
        Long docCount = documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getKbId, kb.getId()));
        vo.setDocumentCount(docCount);

        // 查询所有者姓名
        if (kb.getOwnerId() != null) {
            User owner = userMapper.selectById(kb.getOwnerId());
            if (owner != null) {
                vo.setOwnerName(owner.getRealName() != null ? owner.getRealName() : owner.getUsername());
            }
        }

        return vo;
    }
}
