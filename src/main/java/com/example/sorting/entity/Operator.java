package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运营商实体
 */
@Data
public class Operator {
    private String id;
    private String code;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
