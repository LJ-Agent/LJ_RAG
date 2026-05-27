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
import com.rag.domain.entity.DocumentChunk;
import com.rag.domain.entity.ReviewRecord;
import com.rag.domain.mapper.DocumentChunkMapper;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.domain.mapper.ReviewRecordMapper;
import com.rag.infrastructure.config.MinioConfig;
import com.rag.service.file.FileService;
import com.rag.service.file.dto.FileQueryDTO;
import com.rag.service.file.dto.FileVO;
import com.rag.service.statemachine.DocumentStateMachine;
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
    private final com.rag.domain.mapper.KnowledgeBaseMapper kbMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DocumentChunkMapper chunkMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final DocumentStateMachine stateMachine;

    @Override
    @Transactional
    public Result<FileVO> upload(MultipartFile file, Long kbId, Long userId, String chunkStrategy, String chunkConfig) {
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

        // 3. 去重检查（非删除记录）
        Document existDoc = documentMapper.selectOne(
                new LambdaQueryWrapper<Document>().eq(Document::getFileMd5, md5));
        if (existDoc != null) {
            log.info("文件已存在, MD5={}, 秒传", md5);
            return Result.success(toVO(existDoc));
        }

        // 3.1 检查是否已删除的同MD5记录，若存在则恢复
        Document deletedDoc = documentMapper.selectDeletedByMd5(md5);
        if (deletedDoc != null) {
            log.info("恢复已删除文件: id={}, md5={}", deletedDoc.getId(), md5);
            return Result.success(restoreDocument(deletedDoc, file, kbId, userId, chunkStrategy, chunkConfig));
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

        // 5. 保存元数据（捕获MD5唯一约束冲突，处理并发上传场景）
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setFileName(originalName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setFileMd5(md5);
        doc.setMinioPath(objectName);
        doc.setStatus(DocumentStatus.UPLOADED.name());
        doc.setChunkStrategy(chunkStrategy != null ? chunkStrategy : "semantic");
        doc.setChunkConfig(chunkConfig);
        doc.setUploadUserId(userId);
        doc.setUploadAt(LocalDateTime.now());
        try {
            documentMapper.insert(doc);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("MD5唯一约束冲突（并发上传/重复文件）: md5={}, 回退查询已有记录", md5);
            Document existing = documentMapper.selectOne(
                    new LambdaQueryWrapper<Document>().eq(Document::getFileMd5, md5));
            if (existing != null) {
                return Result.success(toVO(existing));
            }
            // 可能是已删除记录占用唯一索引，再查一次
            Document deleted = documentMapper.selectDeletedByMd5(md5);
            if (deleted != null) {
                log.info("冲突源为已删除记录，恢复: id={}, md5={}", deleted.getId(), md5);
                return Result.success(restoreDocument(deleted, file, kbId, userId, chunkStrategy, chunkConfig));
            }
            throw new BusinessException(ResultCodeEnum.FILE_DUPLICATE);
        }

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
        doDelete(doc);
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> batchDelete(Long[] ids, Long userId) {
        for (Long id : ids) {
            Document doc = documentMapper.selectById(id);
            if (doc != null) {
                doDelete(doc);
            }
        }
        return Result.success();
    }

    private void doDelete(Document doc) {

        // 1. 删除文档块数据（document_chunks 表）
        chunkMapper.delete(new LambdaQueryWrapper<com.rag.domain.entity.DocumentChunk>()
                .eq(com.rag.domain.entity.DocumentChunk::getDocumentId, doc.getId()));

        // 2. 删除审核记录（review_records 表）
        reviewRecordMapper.delete(new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getDocumentId, doc.getId()));

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
        documentMapper.deleteById(doc.getId());

        // 6. 通知Python清理Milvus向量和BM25索引
        sendDocumentDeleteMessage(doc);

        log.info("文档完整删除成功: id={}, fileName={}", doc.getId(), doc.getFileName());
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
                .set("fileType", doc.getFileType())
                .set("chunkStrategy", doc.getChunkStrategy())
                .set("chunkConfig", doc.getChunkConfig()));
        message.setCreatedAt(LocalDateTime.now().toString());

        kafkaTemplate.send(KafkaConstants.TOPIC_FILE_PROCESS, message.getTaskId(), JSONUtil.toJsonStr(message));
        log.info("Kafka消息已发送: topic={}, taskId={}", KafkaConstants.TOPIC_FILE_PROCESS, message.getTaskId());
    }

    private FileVO toVO(Document doc) {
        FileVO vo = new FileVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        // 查询所属知识库名称
        if (doc.getKbId() != null) {
            var kb = kbMapper.selectById(doc.getKbId());
            if (kb != null) {
                vo.setKbName(kb.getKbName());
            }
        }
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setFileMd5(doc.getFileMd5());
        vo.setStatus(doc.getStatus());
        vo.setErrorMessage(doc.getErrorMessage());
        vo.setChunkCount(doc.getChunkCount());
        vo.setChunkStrategy(doc.getChunkStrategy());
        vo.setChunkConfig(doc.getChunkConfig());
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

    private FileVO restoreDocument(Document deletedDoc, MultipartFile file, Long kbId,
                                    Long userId, String chunkStrategy, String chunkConfig) {
        String ext = getFileExtension(deletedDoc.getFileName());
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
            log.error("MinIO上传失败(恢复)", e);
            throw new BusinessException(ResultCodeEnum.FILE_UPLOAD_ERROR);
        }

        documentMapper.restoreById(deletedDoc.getId(), kbId, deletedDoc.getFileName(), ext,
                file.getSize(), objectName, DocumentStatus.UPLOADED.name(),
                chunkStrategy != null ? chunkStrategy : "semantic", chunkConfig,
                userId, LocalDateTime.now());

        Document restored = documentMapper.selectById(deletedDoc.getId());
        sendKafkaMessage(restored);
        log.info("已删除文件恢复成功: id={}, name={}, md5={}", restored.getId(), restored.getFileName(),
                restored.getFileMd5());
        return toVO(restored);
    }

    @Override
    @Transactional
    public Result<FileVO> rechunk(Long id, String chunkStrategy, String chunkConfig) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }
        String currentStatus = doc.getStatus();
        boolean isCompleted = DocumentStatus.COMPLETED.name().equals(currentStatus);
        if (!DocumentStatus.REJECTED.name().equals(currentStatus)
                && !DocumentStatus.CHUNKING_FAILED.name().equals(currentStatus)
                && !isCompleted) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_STATUS_ERROR.getCode(),
                    "当前状态不允许重新分块: " + currentStatus);
        }

        // COMPLETED 状态：先清理 Milvus 向量和 BM25 索引
        if (isCompleted) {
            sendDocumentDeleteMessage(doc);
        }

        // 清除旧分块数据
        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, doc.getId()));
        doc.setChunkCount(0);

        doc.setChunkStrategy(chunkStrategy != null ? chunkStrategy : "semantic");
        doc.setChunkConfig(chunkConfig);
        documentMapper.updateById(doc);

        stateMachine.transit(doc, DocumentStatus.CHUNKING.name());
        sendChunkProcessMessage(doc);

        log.info("重新分块已触发: docId={}, strategy={}, 旧分块已清除", doc.getId(), doc.getChunkStrategy());
        return Result.success(toVO(doc));
    }

    private void sendChunkProcessMessage(Document doc) {
        String taskId = "task-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-chunk-" + doc.getId();
        String cleanedPath = doc.getMinioPath() != null
                ? minioConfig.getBucketName() + "/" + buildCleanedPath(doc.getMinioPath())
                : "";
        KafkaMessage message = new KafkaMessage();
        message.setTaskId(taskId);
        message.setTaskType(TaskType.CHUNK_PROCESS.name());
        message.setDocumentId(doc.getId());
        message.setKbId(doc.getKbId());
        message.setData(JSONUtil.createObj()
                .set("cleanedPath", cleanedPath)
                .set("fileName", doc.getFileName())
                .set("chunkStrategy", doc.getChunkStrategy() != null ? doc.getChunkStrategy() : "semantic")
                .set("chunkConfig", doc.getChunkConfig()));
        message.setCreatedAt(LocalDateTime.now().toString());
        kafkaTemplate.send(KafkaConstants.TOPIC_CHUNK_PROCESS, taskId, JSONUtil.toJsonStr(message));
        log.info("CHUNK_PROCESS消息已发送(重新分块): taskId={}, docId={}", taskId, doc.getId());
    }

    @Override
    public void raw(Long id, HttpServletResponse response) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCodeEnum.DOCUMENT_NOT_FOUND);
        }

        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucketName())
                .object(doc.getMinioPath())
                .build())) {

            String ext = getFileExtension(doc.getFileName());
            String contentType = switch (ext) {
                case "pdf" -> "application/pdf";
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "svg" -> "image/svg+xml";
                case "bmp" -> "image/bmp";
                case "doc" -> "application/msword";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xls" -> "application/vnd.ms-excel";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "ppt" -> "application/vnd.ms-powerpoint";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "txt", "md", "csv" -> "text/plain; charset=UTF-8";
                default -> "application/octet-stream";
            };
            response.setContentType(contentType);
            response.setContentLengthLong(doc.getFileSize());

            // RFC 5987 编码非ASCII文件名
            String encodedName = URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "inline; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, read);
            }
            response.getOutputStream().flush();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取原始文件失败: id={}, minioPath={}", id, doc.getMinioPath(), e);
            throw new BusinessException(ResultCodeEnum.FILE_NOT_FOUND);
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
