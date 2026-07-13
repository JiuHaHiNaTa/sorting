package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分拣任务实体
 */
@Data
public class SortingTask {
    private String id;
    private String fileServerId;
    private String status;
    private String currentStep;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
