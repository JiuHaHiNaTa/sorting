package com.example.sorting.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 话单记录实体
 */
@Data
public class CdrRecord {
    private String id;
    private String taskId;
    private String operatorId;
    private String serviceAzId;
    private String resourceId;
    private BigDecimal usageAmount;
    private String usageUnitId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}
