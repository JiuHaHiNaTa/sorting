package com.example.sorting.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private com.example.sorting.repository.ConfigMapper configMapper;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new org.springframework.web.client.NoOpResponseErrorHandler());
    }

    private HttpEntity<Map<String, ?>> jsonRequest(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(body, headers);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @SuppressWarnings("unchecked")
    void addConfig_shouldReturnSuccess() {
        Map<String, String> request = Map.of(
                "serverAddress", "192.168.1.100",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/config/add"), HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("code"));

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
        assertEquals("192.168.1.100", data.get("serverAddress"));
        assertEquals(false, data.get("connectivityStatus"));
        assertEquals(false, data.get("enabled"));
        // AK/SK 应被掩码处理，不返回明文
        assertEquals("test***", data.get("accessKey"));
        assertEquals("test***", data.get("secretKey"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addConfig_shouldReturnParamErrorWhenFieldMissing() {
        Map<String, String> request = Map.of("serverAddress", "192.168.1.100");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/config/add"), HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addConfig_shouldReturnParamErrorWhenFieldEmpty() {
        Map<String, String> request = Map.of(
                "serverAddress", "",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/config/add"), HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleConfig_shouldReturnConfig002WhenNotConnected() {
        Map<String, String> addReq = Map.of(
                "serverAddress", "192.168.1.100",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );
        ResponseEntity<Map> addResp = restTemplate.exchange(
                url("/config/add"), HttpMethod.POST, jsonRequest(addReq), Map.class);
        String id = (String) ((Map<String, Object>) addResp.getBody().get("data")).get("id");

        Map<String, Object> toggleReq = Map.of("id", id, "enabled", true);

        ResponseEntity<Map> toggleResp = restTemplate.exchange(
                url("/config/toggle"), HttpMethod.POST, jsonRequest(toggleReq), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, toggleResp.getStatusCode());
        assertEquals("CONFIG_002", toggleResp.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modifyConfig_shouldThrowConfig001WhenNotFound() {
        Map<String, String> modifyReq = Map.of(
                "id", UUID.randomUUID().toString(),
                "serverAddress", "192.168.1.200",
                "serverPort", "9001",
                "bucketName", "b",
                "accessKey", "ak",
                "secretKey", "sk",
                "fileDirectory", "/d"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/config/modify"), HttpMethod.POST, jsonRequest(modifyReq), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONFIG_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleConfig_shouldReturnConfig001WhenNotFound() {
        Map<String, Object> toggleReq = Map.of(
                "id", UUID.randomUUID().toString(),
                "enabled", false
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/config/toggle"), HttpMethod.POST, jsonRequest(toggleReq), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONFIG_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toggleConfig_shouldAllowDisableWithoutConnectivity() {
        Map<String, String> addReq = Map.of(
                "serverAddress", "192.168.1.100",
                "serverPort", "9000",
                "bucketName", "test-bucket",
                "accessKey", "test-ak",
                "secretKey", "test-sk",
                "fileDirectory", "/data/cdr"
        );
        ResponseEntity<Map> addResp = restTemplate.exchange(
                url("/config/add"), HttpMethod.POST, jsonRequest(addReq), Map.class);
        String id = (String) ((Map<String, Object>) addResp.getBody().get("data")).get("id");

        Map<String, Object> toggleReq = Map.of("id", id, "enabled", false);
        ResponseEntity<Map> toggleResp = restTemplate.exchange(
                url("/config/toggle"), HttpMethod.POST, jsonRequest(toggleReq), Map.class);

        assertEquals(HttpStatus.OK, toggleResp.getStatusCode());
        assertEquals("SUCCESS", toggleResp.getBody().get("code"));
    }
}
