package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfigAddReq {

    @NotBlank(message = "服务器地址不能为空")
    private String serverAddress;

    @NotBlank(message = "服务器端口不能为空")
    private String serverPort;

    @NotBlank(message = "存储桶名称不能为空")
    private String bucketName;

    @NotBlank(message = "访问密钥不能为空")
    private String accessKey;

    @NotBlank(message = "秘密密钥不能为空")
    private String secretKey;

    @NotBlank(message = "文件目录不能为空")
    private String fileDirectory;

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
}
