package com.example.sorting.entity;

import java.time.LocalDateTime;

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

    public FileServerConfig() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }

    public String getServerPort() { return serverPort; }
    public void setServerPort(String serverPort) { this.serverPort = serverPort; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getFileDirectory() { return fileDirectory; }
    public void setFileDirectory(String fileDirectory) { this.fileDirectory = fileDirectory; }

    public Boolean getConnectivityStatus() { return connectivityStatus; }
    public void setConnectivityStatus(Boolean connectivityStatus) { this.connectivityStatus = connectivityStatus; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
