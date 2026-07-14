package com.example.sorting.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsageUnitControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

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
    void add_shouldReturnSuccess() {
        Map<String, String> request = Map.of("code", "MB", "name", "兆字节");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("code"));

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
        assertEquals("MB", data.get("code"));
        assertEquals("兆字节", data.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void add_shouldReturnParamErrorWhenFieldMissing() {
        Map<String, String> request = Map.of("code", "MissingName");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void add_shouldReturnParamErrorWhenFieldEmpty() {
        Map<String, String> request = Map.of("code", "EmptyName", "name", "");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(request), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PARAM_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void add_shouldReturnUnit002WhenDuplicateCode() {
        Map<String, String> request = Map.of("code", "DupUnit", "name", "重复单位");

        ResponseEntity<Map> firstResp = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(request), Map.class);
        assertEquals(HttpStatus.OK, firstResp.getStatusCode());

        ResponseEntity<Map> secondResp = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(request), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, secondResp.getStatusCode());
        assertEquals("UNIT_002", secondResp.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modify_shouldReturnSuccess() {
        Map<String, String> addReq = Map.of("code", "ModUnit", "name", "修改前");
        ResponseEntity<Map> addResp = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(addReq), Map.class);
        String id = (String) ((Map<String, Object>) addResp.getBody().get("data")).get("id");

        Map<String, String> modifyReq = Map.of("id", id, "name", "修改后");
        ResponseEntity<Map> modifyResp = restTemplate.exchange(
                url("/usage-unit/modify"), HttpMethod.POST, jsonRequest(modifyReq), Map.class);

        assertEquals(HttpStatus.OK, modifyResp.getStatusCode());
        assertEquals("SUCCESS", modifyResp.getBody().get("code"));

        Map<String, Object> data = (Map<String, Object>) modifyResp.getBody().get("data");
        assertEquals("修改后", data.get("name"));
        assertEquals("ModUnit", data.get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modify_shouldReturnUnit001WhenNotFound() {
        Map<String, String> modifyReq = Map.of("id", UUID.randomUUID().toString(), "name", "不存在");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/usage-unit/modify"), HttpMethod.POST, jsonRequest(modifyReq), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("UNIT_001", response.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modify_shouldReturnUnit002WhenDuplicateCode() {
        Map<String, String> reqA = Map.of("code", "UnitConfA", "name", "单位A");
        ResponseEntity<Map> respA = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(reqA), Map.class);
        assertEquals(HttpStatus.OK, respA.getStatusCode());

        Map<String, String> reqB = Map.of("code", "UnitConfB", "name", "单位B");
        ResponseEntity<Map> respB = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(reqB), Map.class);
        assertEquals(HttpStatus.OK, respB.getStatusCode());

        String idA = (String) ((Map<String, Object>) respA.getBody().get("data")).get("id");

        Map<String, String> modifyReq = Map.of("id", idA, "code", "UnitConfB");
        ResponseEntity<Map> modifyResp = restTemplate.exchange(
                url("/usage-unit/modify"), HttpMethod.POST, jsonRequest(modifyReq), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, modifyResp.getStatusCode());
        assertEquals("UNIT_002", modifyResp.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_shouldReturnSuccess() {
        Map<String, String> addReq = Map.of("code", "ListUnit", "name", "列表测试");
        restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(addReq), Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/usage-unit/list"), HttpMethod.POST, jsonRequest(Map.of()), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("code"));

        Object data = response.getBody().get("data");
        assertTrue(data instanceof java.util.List);
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_shouldReturnSuccess() {
        Map<String, String> addReq = Map.of("code", "DelUnit", "name", "待删除");
        ResponseEntity<Map> addResp = restTemplate.exchange(
                url("/usage-unit/add"), HttpMethod.POST, jsonRequest(addReq), Map.class);
        String id = (String) ((Map<String, Object>) addResp.getBody().get("data")).get("id");

        Map<String, String> deleteReq = Map.of("id", id);
        ResponseEntity<Map> deleteResp = restTemplate.exchange(
                url("/usage-unit/delete"), HttpMethod.POST, jsonRequest(deleteReq), Map.class);

        assertEquals(HttpStatus.OK, deleteResp.getStatusCode());
        assertEquals("SUCCESS", deleteResp.getBody().get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_shouldReturnUnit001WhenNotFound() {
        Map<String, String> deleteReq = Map.of("id", UUID.randomUUID().toString());

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/usage-unit/delete"), HttpMethod.POST, jsonRequest(deleteReq), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("UNIT_001", response.getBody().get("code"));
    }
}
