package com.rag.communication.kafka.consumer;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rag.common.constant.KafkaConstants;
import com.rag.common.enums.TaskType;
import com.rag.communication.kafka.dto.KafkaMessage;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.DocumentChunk;
import com.rag.domain.mapper.DocumentChunkMapper;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.service.statemachine.DocumentStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCompleteConsumer {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentStateMachine stateMachine;

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

            stateMachine.transitToNext(doc);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("处理任务完成通知失败: taskId={}, docId={}",
                    message != null ? message.getTaskId() : "unknown",
                    message != null ? message.getDocumentId() : "unknown", e);
            // Do NOT acknowledge on failure — message will be re-delivered
        }
    }

    private void saveChunkData(Document doc, KafkaMessage message) {
        Object data = message.getData();
        JSONArray chunks;
        if (data instanceof JSONArray) {
            chunks = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            chunks = ((JSONObject) data).getJSONArray("chunks");
        } else {
            // Data might be a map from deserialization
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

        // Update chunk count on document
        doc.setChunkCount(savedCount);
        documentMapper.updateById(doc);
        log.info("Chunk数据已保存: docId={}, chunkCount={}", doc.getId(), savedCount);
    }
}
