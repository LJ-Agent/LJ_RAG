package com.rag.communication.kafka.consumer;

import cn.hutool.json.JSONUtil;
import com.rag.common.constant.KafkaConstants;
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
public class TaskCompleteConsumer {

    private final DocumentMapper documentMapper;
    private final DocumentStateMachine stateMachine;

    @KafkaListener(topics = KafkaConstants.TOPIC_TASK_COMPLETE, groupId = KafkaConstants.CONSUMER_GROUP)
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            KafkaMessage message = JSONUtil.toBean(record.value(), KafkaMessage.class);
            log.info("收到任务完成通知: taskId={}, type={}, docId={}",
                    message.getTaskId(), message.getTaskType(), message.getDocumentId());

            Document doc = documentMapper.selectById(message.getDocumentId());
            if (doc == null) {
                log.warn("文档不存在: docId={}", message.getDocumentId());
                acknowledgment.acknowledge();
                return;
            }

            stateMachine.transitToNext(doc);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("处理任务完成通知失败", e);
        }
    }
}
