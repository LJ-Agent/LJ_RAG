package com.rag.communication.kafka.consumer;

import cn.hutool.json.JSONUtil;
import com.rag.common.constant.KafkaConstants;
import com.rag.common.enums.DocumentStatus;
import com.rag.communication.kafka.dto.KafkaMessage;
import com.rag.domain.entity.Document;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.service.statemachine.DocumentStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskFailedConsumer {

    private final DocumentMapper documentMapper;
    private final DocumentStateMachine stateMachine;

    @KafkaListener(topics = KafkaConstants.TOPIC_TASK_FAILED, groupId = KafkaConstants.CONSUMER_GROUP)
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            KafkaMessage message = JSONUtil.toBean(record.value(), KafkaMessage.class);
            log.warn("收到任务失败通知: taskId={}, type={}, docId={}",
                    message.getTaskId(), message.getTaskType(), message.getDocumentId());

            Document doc = documentMapper.selectById(message.getDocumentId());
            if (doc == null) {
                log.warn("文档不存在: docId={}", message.getDocumentId());
                acknowledgment.acknowledge();
                return;
            }

            // 根据当前状态映射到对应的失败状态
            DocumentStatus current = DocumentStatus.valueOf(doc.getStatus());
            DocumentStatus failed = switch (current) {
                case PARSING, UPLOADED -> DocumentStatus.PARSING_FAILED;
                case CLEANING -> DocumentStatus.CLEANING_FAILED;
                case CHUNKING -> DocumentStatus.CHUNKING_FAILED;
                case EMBEDDING -> DocumentStatus.EMBEDDING_FAILED;
                default -> current;
            };

            String error = "Python任务失败: " + message.getTaskType();
            stateMachine.fail(doc, failed, error);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("处理任务失败通知异常", e);
        }
    }
}
