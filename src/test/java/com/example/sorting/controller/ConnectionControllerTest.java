package com.example.sorting.controller;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.ConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
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

    private HttpEntity<Map<String, ?>> jsonRequest(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(body, headers);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnConnectionErrorWhenMinioUnreachable() {
        Map<String, String> request = Map.of("id", configId);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/connection/check", HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String code = (String) response.getBody().get("code");
        assertTrue(code.equals("CNN_001") || code.equals("CNN_003"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnConfig001WhenNotFound() {
        Map<String, String> request = Map.of("id", UUID.randomUUID().toString());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/connection/check", HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONFIG_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkConnection_shouldReturnParamErrorWhenIdBlank() {
        Map<String, String> request = Map.of("id", "");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/connection/check", HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }
}
