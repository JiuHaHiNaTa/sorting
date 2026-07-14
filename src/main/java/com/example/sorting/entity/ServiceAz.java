package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务可用区实体
 */
@Data
public class ServiceAz {
    private String id;
    private String code;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
