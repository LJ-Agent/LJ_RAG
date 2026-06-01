package com.rag.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.domain.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
