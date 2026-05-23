package com.rag.communication.kafka.producer;

import cn.hutool.json.JSONUtil;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 发送消息到指定Topic（同步发送 + 回调）。
     */
    public void send(String topic, String key, Object message) {
        String json = message instanceof String ? (String) message : JSONUtil.toJsonStr(message);
        try {
            kafkaTemplate.send(topic, key, json).get(5, TimeUnit.SECONDS);
            log.info("Kafka消息发送成功: topic={}, key={}", topic, key);
        } catch (Exception e) {
            log.error("Kafka消息发送失败: topic={}, key={}", topic, key, e);
            throw new BusinessException(ResultCodeEnum.KAFKA_SEND_ERROR);
        }
    }

    /**
     * 异步发送消息（不等待确认）。
     */
    public void sendAsync(String topic, String key, Object message) {
        String json = message instanceof String ? (String) message : JSONUtil.toJsonStr(message);
        kafkaTemplate.send(topic, key, json);
    }
}
