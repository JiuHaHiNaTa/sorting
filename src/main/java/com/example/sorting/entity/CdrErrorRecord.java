package com.example.sorting.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CdrErrorRecord {
    private String id;
    private String taskId;
    private String fileName;
    private String errorType;
    private String errorReason;
    private LocalDateTime createdAt;
}