package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 使用量单位实体
 */
@Data
public class UsageUnit {
    private String id;
    private String code;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
