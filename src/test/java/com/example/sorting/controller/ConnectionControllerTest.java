package com.example.sorting.controller;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.ConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ConfigMapper configMapper;

    private RestTemplate restTemplate;
    private String baseUrl;
    private String configId;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        // 不将 4xx 响应视为异常，以便测试能正常获取响应体
        restTemplate.setErrorHandler(new org.springframework.web.client.NoOpResponseErrorHandler());
        baseUrl = "http://localhost:" + port;

        // 准备一条测试配置
        FileServerConfig config = new FileServerConfig();
        configId = UUID.randomUUID().toString();
        config.setId(configId);
        config.setServerAddress("192.168.1.100");
        config.setServerPort("9000");
        config.setBucketName("test-bucket");
        config.setAccessKey("test-ak");
        config.setSecretKey("test-sk");
        config.setFileDirectory("/data/cdr");
        config.setConnectivityStatus(false);
        config.setEnabled(false);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(config);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnConnectionErrorWhenMinioUnreachable() {
        Map<String, String> request = Map.of("id", configId);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/connection/check", request, Map.class);

        // 没有真实 MinIO 服务器，预期返回连通性相关错误码
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String code = (String) response.getBody().get("code");
        assertTrue(code.equals("CNN_001") || code.equals("CNN_003"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnConfig001WhenNotFound() {
        Map<String, String> request = Map.of("id", UUID.randomUUID().toString());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/connection/check", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONFIG_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnParamErrorWhenIdBlank() {
        Map<String, String> request = Map.of("id", "");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/connection/check", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }
}
