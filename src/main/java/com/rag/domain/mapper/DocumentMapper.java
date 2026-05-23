package com.rag.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.domain.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 分页查询文档（关联最新审核记录）。
     */
    Page<Document> selectPageWithReviewStatus(Page<Document> page, @Param("query") Map<String, Object> query);
}
