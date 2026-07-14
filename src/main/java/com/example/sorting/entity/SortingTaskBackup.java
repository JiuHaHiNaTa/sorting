package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分拣任务备份实体（已完成任务归档）
 */
@Data
public class SortingTaskBackup {
    private String id;
    private String fileServerId;
    private String status;
    private String currentStep;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
