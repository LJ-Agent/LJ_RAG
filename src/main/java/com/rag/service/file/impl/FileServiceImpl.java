package com.rag.service.file.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.constant.KafkaConstants;
import com.rag.common.enums.DocumentStatus;
import com.rag.common.enums.TaskType;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.common.util.Md5Util;
import com.rag.communication.kafka.dto.KafkaMessage;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.ReviewRecord;
import com.rag.domain.mapper.DocumentChunkMapper;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.domain.mapper.ReviewRecordMapper;
import com.rag.infrastructure.config.MinioConfig;
import com.rag.service.file.FileService;
import com.rag.service.file.dto.FileQueryDTO;
import com.rag.service.file.dto.FileVO;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final DocumentMapper documentMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DocumentChunkMapper chunkMapper;
    private final ReviewRecordMapper reviewRecordMapper;

    @Override
    @Transactional
    public Result<FileVO> upload(MultipartFile file, Long kbId, Long userId, String chunkStrategy) {
        // 1. 参数校验
        if (file.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String ext = getFileExtension(originalName);

        // 2. MD5计算
        String md5;
        try (InputStream is = file.getInputStream()) {
            md5 = Md5Util.md5(is);
        } catch (Exception e) {
            log.error("MD5计算失败", e);
            throw new BusinessException(ResultCodeEnum.FILE_UPLOAD_ERROR);
        }

        // 3. 去重检查
        Document existDoc = documentMapper.selectOne(
                new LambdaQueryWrapper<Document>().eq(Document::getFileMd5, md5));
        if (existDoc != null) {
            log.info("文件已存在, MD5={}, 秒传", md5);
            return Result.success(toVO(existDoc));
        }

        // 4. 上传到MinIO
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String objectName = datePath + "/" + IdUtil.fastSimpleUUID() + "." + ext;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO上传失败", e);
            throw new BusinessException(ResultCodeEnum.FILE_UPLOAD_ERROR);
        }

        // 5. 保存元数据
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setFileName(originalName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setFileMd5(md5);
        doc.setMinioPath(objectName);
        doc.setStatus(DocumentStatus.UPLOADED.name());
        doc.setChunkStrategy(chunkStrategy != null ? chunkStrategy : "semantic");
        doc.setUploadUserId(userId);
        doc.setUploadAt(LocalDateTime.now());
        documentMapper.insert(doc);

        // 6. 发送Kafka消息
        sendKafkaMessage(doc);

        log.info("文件上传成功: id={}, name={}, md5={}", doc.getId(), originalName, md5);
        return Result.success(toVO(doc));
    }

    @Override
    public Result<Page<FileVO>> list(FileQueryDTO query) {
        Page<Document> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        if (query.getKbId() != null) {
            wrapper.eq(Document::getKbId, query.getKbId());
        }
        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.eq(Document::getStatus, query.getStatus());
        }
        if (query.getFileName() != null && !query.getFileName().isEmpty()) {
            wrapper.like(Document::getFileName, query.getFileName());
        }
        wrapper.orderByDesc(Document::getCreatedAt);

        Page<Document> result = documentMapper.selectPage(page, wrapper);
        Page<FileVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return Result.success(voPage);
    }

    @Override
    public Result<FileVO> detail(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }
        return Result.success(toVO(doc));
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id, Long userId) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }

        // 1. 删除文档块数据（document_chunks 表）
        chunkMapper.delete(new LambdaQueryWrapper<com.rag.domain.entity.DocumentChunk>()
                .eq(com.rag.domain.entity.DocumentChunk::getDocumentId, id));

        // 2. 删除审核记录（review_records 表）
        reviewRecordMapper.delete(new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getDocumentId, id));

        // 3. 删除MinIO原文件
        try {
            minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(doc.getMinioPath())
                    .build());
        } catch (Exception e) {
            log.warn("MinIO原文件删除失败: {}", doc.getMinioPath(), e);
        }

        // 4. 删除MinIO清洗文件
        String cleanedPath = buildCleanedPath(doc.getMinioPath());
        if (cleanedPath != null) {
            try {
                minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(cleanedPath)
                        .build());
            } catch (Exception e) {
                log.warn("MinIO清洗文件删除失败: {}", cleanedPath, e);
            }
        }

        // 5. 删除文档元数据
        documentMapper.deleteById(id);

        // 6. 通知Python清理Milvus向量和BM25索引
        sendDocumentDeleteMessage(doc);

        log.info("文档完整删除成功: id={}, fileName={}", id, doc.getFileName());
        return Result.success();
    }

    private void sendDocumentDeleteMessage(Document doc) {
        try {
            KafkaMessage message = new KafkaMessage();
            message.setTaskId("delete-" + doc.getId() + "-" + System.currentTimeMillis());
            message.setTaskType("DOCUMENT_DELETE");
            message.setDocumentId(doc.getId());
            message.setKbId(doc.getKbId());
            message.setCreatedAt(LocalDateTime.now().toString());
            kafkaTemplate.send(KafkaConstants.TOPIC_DOCUMENT_DELETE, message.getTaskId(),
                    JSONUtil.toJsonStr(message));
            log.info("DOCUMENT_DELETE消息已发送: docId={}", doc.getId());
        } catch (Exception e) {
            log.error("发送DOCUMENT_DELETE消息失败: docId={}", doc.getId(), e);
        }
    }

    @Override
    public void getContent(Long id, HttpServletResponse response) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }

        try {
            String cleanedObjectName = buildCleanedPath(doc.getMinioPath());
            if (cleanedObjectName == null) {
                throw new BusinessException(ResultCodeEnum.FILE_NOT_FOUND.getCode(), "文档路径无效");
            }
            InputStream is = getContentStream(doc, cleanedObjectName);

            response.setContentType("text/plain; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");

            try (is) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, read);
                }
                response.getOutputStream().flush();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取文档内容失败: id={}", id, e);
            throw new BusinessException(ResultCodeEnum.FILE_NOT_FOUND);
        }
    }

    @Override
    public void download(Long id, HttpServletResponse response) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }

        try {
            InputStream is = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(doc.getMinioPath())
                    .build());

            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8));

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, read);
            }
            is.close();
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("文件下载失败: id={}", id, e);
            throw new BusinessException(ResultCodeEnum.FILE_NOT_FOUND);
        }
    }

    private void sendKafkaMessage(Document doc) {
        KafkaMessage message = new KafkaMessage();
        message.setTaskId("task-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + doc.getId());
        message.setTaskType(TaskType.FILE_PROCESS.name());
        message.setDocumentId(doc.getId());
        message.setKbId(doc.getKbId());
        message.setData(JSONUtil.createObj()
                .set("originalFileUrl", minioConfig.getBucketName() + "/" + doc.getMinioPath())
                .set("fileName", doc.getFileName())
                .set("fileType", doc.getFileType()));
        message.setCreatedAt(LocalDateTime.now().toString());

        kafkaTemplate.send(KafkaConstants.TOPIC_FILE_PROCESS, message.getTaskId(), JSONUtil.toJsonStr(message));
        log.info("Kafka消息已发送: topic={}, taskId={}", KafkaConstants.TOPIC_FILE_PROCESS, message.getTaskId());
    }

    private FileVO toVO(Document doc) {
        FileVO vo = new FileVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setFileMd5(doc.getFileMd5());
        vo.setStatus(doc.getStatus());
        vo.setErrorMessage(doc.getErrorMessage());
        vo.setChunkCount(doc.getChunkCount());
        vo.setChunkStrategy(doc.getChunkStrategy());
        vo.setUploadUserId(doc.getUploadUserId());
        vo.setUploadAt(doc.getUploadAt());
        vo.setCompletedAt(doc.getCompletedAt());
        vo.setCreatedAt(doc.getCreatedAt());
        return vo;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    @Override
    public Result<String> getPresignedUrl(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }

        try {
            String url = minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(doc.getMinioPath())
                            .expiry(60 * 10) // 10 minutes
                            .build());
            return Result.success(url);
        } catch (Exception e) {
            log.error("生成预签名URL失败: id={}", id, e);
            throw new BusinessException(ResultCodeEnum.FILE_UPLOAD_ERROR);
        }
    }

    private String buildCleanedPath(String minioPath) {
        if (minioPath == null) return null;
        int lastDot = minioPath.lastIndexOf('.');
        if (lastDot > 0) {
            return minioPath.substring(0, lastDot) + "_cleaned.md";
        }
        return minioPath + "_cleaned.md";
    }

    private InputStream getContentStream(Document doc, String cleanedObjectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(cleanedObjectName)
                    .build());
        } catch (Exception e) {
            String ext = getFileExtension(doc.getFileName());
            if (!"txt".equals(ext) && !"md".equals(ext)) {
                throw new BusinessException(ResultCodeEnum.FILE_NOT_FOUND.getCode(), "文档内容尚未生成，请等待处理完成");
            }
            try {
                return minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(doc.getMinioPath())
                        .build());
            } catch (Exception ex) {
                throw new BusinessException(ResultCodeEnum.FILE_NOT_FOUND);
            }
        }
    }
}
