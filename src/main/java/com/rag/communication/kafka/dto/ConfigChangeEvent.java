package com.rag.communication.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigChangeEvent {
    private String configKey;
    private String oldValue;
    private String newValue;
    private String valueType;
    private String category;
    private String reloadStrategy;
    private String targetServices;
    private Long changedBy;
    private String changedByName;
    private String changedAt;
}
