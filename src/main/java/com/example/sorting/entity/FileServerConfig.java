package com.example.sorting.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件服务器配置实体（v0.0.1）
 */
@Data
public class FileServerConfig {
    private String id;
    private String serverAddress;
    private String serverPort;
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private String fileDirectory;
    private Boolean connectivityStatus;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
