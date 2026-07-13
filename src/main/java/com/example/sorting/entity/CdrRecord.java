package com.example.sorting.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CDR 话单记录实体
 */
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getServiceAzId() {
        return serviceAzId;
    }

    public void setServiceAzId(String serviceAzId) {
        this.serviceAzId = serviceAzId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public BigDecimal getUsageAmount() {
        return usageAmount;
    }

    public void setUsageAmount(BigDecimal usageAmount) {
        this.usageAmount = usageAmount;
    }

    public String getUsageUnitId() {
        return usageUnitId;
    }

    public void setUsageUnitId(String usageUnitId) {
        this.usageUnitId = usageUnitId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
