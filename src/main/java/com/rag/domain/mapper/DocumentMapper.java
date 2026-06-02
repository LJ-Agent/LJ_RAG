package com.rag.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.domain.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 分页查询文档（关联最新审核记录）。
     */
    Page<Document> selectPageWithReviewStatus(Page<Document> page, @Param("query") Map<String, Object> query);

    /**
     * 根据MD5查已删除记录（绕过 @TableLogic，用于恢复场景）。
     */
    @Select("SELECT * FROM documents WHERE file_md5 = #{md5} AND deleted = 1 LIMIT 1")
    Document selectDeletedByMd5(@Param("md5") String md5);

    /**
     * 恢复已删除的记录（绕过 @TableLogic 的 WHERE deleted=0 限制）。
     */
    @Update("UPDATE documents SET kb_id=#{kbId}, file_name=#{fileName}, file_type=#{fileType}, file_size=#{fileSize}, minio_path=#{minioPath}, status=#{status}, chunk_strategy=#{chunkStrategy}, chunk_config=#{chunkConfig}, upload_user_id=#{uploadUserId}, upload_at=#{uploadAt}, deleted=0 WHERE id=#{id}")
    int restoreById(@Param("id") Long id, @Param("kbId") Long kbId, @Param("fileName") String fileName,
                     @Param("fileType") String fileType, @Param("fileSize") Long fileSize,
                     @Param("minioPath") String minioPath, @Param("status") String status,
                     @Param("chunkStrategy") String chunkStrategy, @Param("chunkConfig") String chunkConfig,
                     @Param("uploadUserId") Long uploadUserId, @Param("uploadAt") LocalDateTime uploadAt);
}
