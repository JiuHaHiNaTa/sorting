package com.example.sorting.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CdrPushRecord {
    private String id;
    private String cdrRecordId;
    private String pushStatus;
    private LocalDateTime pushedAt;
    private String failReason;
    private LocalDateTime createdAt;
}