package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分拣任务步骤日志实体
 */
@Data
public class SortingStepLog {
    private String id;
    private String taskId;
    private String stepName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String detail;
}
