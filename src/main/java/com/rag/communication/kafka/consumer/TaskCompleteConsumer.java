package com.rag.communication.kafka.consumer;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rag.common.constant.KafkaConstants;
import com.rag.common.enums.DocumentStatus;
import com.rag.common.enums.TaskType;
import com.rag.communication.kafka.dto.KafkaMessage;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.DocumentChunk;
import com.rag.domain.mapper.DocumentChunkMapper;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.infrastructure.config.MinioConfig;
import com.rag.service.statemachine.DocumentStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCompleteConsumer {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentStateMachine stateMachine;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MinioConfig minioConfig;

    @KafkaListener(topics = KafkaConstants.TOPIC_TASK_COMPLETE, groupId = KafkaConstants.CONSUMER_GROUP)
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        KafkaMessage message = null;
        try {
            message = JSONUtil.toBean(record.value(), KafkaMessage.class);
            log.info("收到任务完成通知: taskId={}, type={}, docId={}",
                    message.getTaskId(), message.getTaskType(), message.getDocumentId());

            // DOCUMENT_DELETE completion — document already removed, just ack
            if ("DOCUMENT_DELETE".equals(message.getTaskType())) {
                acknowledgment.acknowledge();
                return;
            }

            Document doc = documentMapper.selectById(message.getDocumentId());
            if (doc == null) {
                log.warn("文档不存在: docId={}", message.getDocumentId());
                acknowledgment.acknowledge();
                return;
            }

            // Save chunk data if present in CHUNK_PROCESS completion
            if (TaskType.CHUNK_PROCESS.name().equals(message.getTaskType()) && message.getData() != null) {
                saveChunkData(doc, message);
            }

            // State transition: FILE_PROCESS → CHUNKING, CHUNK_PROCESS → CHUNK_REVIEW, EMBED_PROCESS → COMPLETED
            DocumentStatus before = DocumentStatus.valueOf(doc.getStatus());
            stateMachine.transitToNext(doc);
            DocumentStatus after = DocumentStatus.valueOf(doc.getStatus());

            // After FILE_PROCESS completes, auto-trigger CHUNK_PROCESS
            boolean isFileProcessComplete = (before == DocumentStatus.UPLOADED && after == DocumentStatus.CHUNKING)
                    || (before == DocumentStatus.CHUNKING && after == DocumentStatus.CHUNK_REVIEW);
            if (isFileProcessComplete && TaskType.FILE_PROCESS.name().equals(message.getTaskType())) {
                String cleanedPath = message.getData() != null ? message.getData().getStr("cleanedPath") : null;
                sendChunkProcessMessage(doc, cleanedPath);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("处理任务完成通知失败: taskId={}, docId={}",
                    message != null ? message.getTaskId() : "unknown",
                    message != null ? message.getDocumentId() : "unknown", e);
            // Do NOT acknowledge on failure — message will be re-delivered
        }
    }

    private void sendChunkProcessMessage(Document doc, String pythonCleanedPath) {
        String taskId = "task-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-chunk-" + doc.getId();
        // Prefer the cleaned path from Python (matches RAG-CLEANING output), fall back to computed
        String cleanedPath = (pythonCleanedPath != null && !pythonCleanedPath.isEmpty())
                ? pythonCleanedPath
                : (doc.getMinioPath() != null
                    ? minioConfig.getBucketName() + "/" + buildCleanedPath(doc.getMinioPath())
                    : "");
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
        log.info("预分块消息已发送(自动): taskId={}, docId={}", taskId, doc.getId());
    }

    private String buildCleanedPath(String minioPath) {
        if (minioPath == null) return "";
        int lastDot = minioPath.lastIndexOf('.');
        if (lastDot > 0) {
            return minioPath.substring(0, lastDot) + "_cleaned.md";
        }
        return minioPath + "_cleaned.md";
    }

    private void saveChunkData(Document doc, KafkaMessage message) {
        Object data = message.getData();
        JSONArray chunks;
        if (data instanceof JSONArray) {
            chunks = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            chunks = ((JSONObject) data).getJSONArray("chunks");
        } else {
            String json = JSONUtil.toJsonStr(data);
            JSONObject obj = JSONUtil.parseObj(json);
            chunks = obj.getJSONArray("chunks");
        }
        if (chunks == null || chunks.isEmpty()) {
            log.info("CHUNK_PROCESS完成但无chunk数据: docId={}", doc.getId());
            return;
        }

        // Delete old chunks for this document (re-chunk scenario)
        chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, doc.getId()));

        int savedCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            JSONObject c = chunks.getJSONObject(i);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setChunkId(c.getStr("chunkId", c.getStr("chunk_id", "")));
            chunk.setChunkIndex(c.getInt("chunkIndex", c.getInt("chunk_index", i)));
            chunk.setContent(c.getStr("content", ""));
            chunk.setLevel(c.getInt("level", 0));
            chunk.setParentId(c.getStr("parentId", c.getStr("parent_id", null)));
            chunk.setCharCount(c.getInt("charCount", c.getInt("char_count", chunk.getContent().length())));
            chunk.setStatus("ACTIVE");
            chunk.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(chunk);
            savedCount++;
        }

        doc.setChunkCount(savedCount);
        documentMapper.updateById(doc);
        log.info("Chunk数据已保存: docId={}, chunkCount={}", doc.getId(), savedCount);
    }
}
