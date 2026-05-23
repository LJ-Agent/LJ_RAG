package com.rag.communication.kafka.dto;

import lombok.Data;

/**
 * Kafka消息体，统一所有任务消息的格式。
 */
@Data
public class KafkaMessage {

    private String taskId;
    private String taskType;
    private Long documentId;
    private Long kbId;
    private Object data;
    private String createdAt;
}
