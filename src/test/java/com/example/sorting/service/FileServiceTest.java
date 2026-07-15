package com.example.sorting.service;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    private FileService fileService;
    private FileServerConfig config;

    @BeforeEach
    void setUp() {
        fileService = new FileService();
        config = new FileServerConfig();
        config.setServerAddress("localhost");
        config.setServerPort("9000");
        config.setBucketName("test-bucket");
        config.setAccessKey("test-ak");
        config.setSecretKey("test-sk");
        config.setFileDirectory("/cdr/incoming");
    }

    @Test
    void buildArchivePath_shouldFormatCorrectly() {
        String path = fileService.buildArchivePath("/backup", "yyyyMMdd", "test.zip");
        String today = java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertEquals("/backup/" + today + "/test.zip", path);
    }

    @Test
    void createClient_shouldBuildFromConfig() {
        MinioClient client = fileService.createClient(config);
        assertNotNull(client);
    }
}
