package com.example.sorting.service;

import com.example.sorting.dto.ConfigAddReq;
import com.example.sorting.dto.ConfigModifyReq;
import com.example.sorting.dto.ConfigToggleReq;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.ConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ConfigMapper configMapper;

    @InjectMocks
    private ConfigService configService;

    @Captor
    private ArgumentCaptor<FileServerConfig> configCaptor;

    private ConfigAddReq addReq;
    private FileServerConfig existingConfig;

    @BeforeEach
    void setUp() {
        addReq = new ConfigAddReq();
        addReq.setServerAddress("192.168.1.100");
        addReq.setServerPort("9000");
        addReq.setBucketName("test-bucket");
        addReq.setAccessKey("test-ak");
        addReq.setSecretKey("test-sk");
        addReq.setFileDirectory("/data/cdr");

        existingConfig = new FileServerConfig();
        existingConfig.setId(UUID.randomUUID().toString());
        existingConfig.setServerAddress("192.168.1.100");
        existingConfig.setServerPort("9000");
        existingConfig.setBucketName("test-bucket");
        existingConfig.setAccessKey("test-ak");
        existingConfig.setSecretKey("test-sk");
        existingConfig.setFileDirectory("/data/cdr");
        existingConfig.setConnectivityStatus(false);
        existingConfig.setEnabled(false);
    }

    @Test
    void addConfig_shouldGenerateIdAndInsert() {
        FileServerConfig result = configService.addConfig(addReq);

        verify(configMapper).insert(configCaptor.capture());
        FileServerConfig captured = configCaptor.getValue();

        assertNotNull(captured.getId());
        assertTrue(captured.getId().matches("[a-f0-9-]{36}"));
        assertEquals("192.168.1.100", captured.getServerAddress());
        assertEquals("9000", captured.getServerPort());
        assertFalse(captured.getConnectivityStatus());
        assertFalse(captured.getEnabled());
        assertNotNull(captured.getCreatedAt());
        assertNotNull(captured.getUpdatedAt());
        assertEquals(captured.getId(), result.getId());
    }

    @Test
    void addConfig_shouldSanitizeInput() {
        addReq.setServerAddress("<script>alert(1)</script>");
        configService.addConfig(addReq);

        verify(configMapper).insert(configCaptor.capture());
        assertFalse(configCaptor.getValue().getServerAddress().contains("<script>"));
    }

    @Test
    void modifyConfig_shouldThrowWhenConfigNotFound() {
        ConfigModifyReq req = new ConfigModifyReq();
        req.setId("non-existent-id");
        req.setServerAddress("192.168.1.200");
        req.setServerPort("9000");
        req.setBucketName("b");
        req.setAccessKey("ak");
        req.setSecretKey("sk");
        req.setFileDirectory("/d");

        when(configMapper.findById("non-existent-id")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.modifyConfig(req));
        assertEquals(ErrorCode.CONFIG_001.getCode(), ex.getCode());
    }

    @Test
    void modifyConfig_shouldUpdateFields() {
        ConfigModifyReq req = new ConfigModifyReq();
        req.setId(existingConfig.getId());
        req.setServerAddress("192.168.1.200");
        req.setServerPort("9001");
        req.setBucketName("new-bucket");
        req.setAccessKey("new-ak");
        req.setSecretKey("new-sk");
        req.setFileDirectory("/data/new");

        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        configService.modifyConfig(req);

        verify(configMapper).update(configCaptor.capture());
        FileServerConfig updated = configCaptor.getValue();
        assertEquals("192.168.1.200", updated.getServerAddress());
        assertEquals("9001", updated.getServerPort());
        assertEquals("new-bucket", updated.getBucketName());
        assertEquals("new-ak", updated.getAccessKey());
        assertEquals("new-sk", updated.getSecretKey());
        assertEquals("/data/new", updated.getFileDirectory());
        assertFalse(updated.getConnectivityStatus());
        assertFalse(updated.getEnabled());
    }

    @Test
    void toggleConfig_shouldThrowWhenConfigNotFound() {
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId("non-existent");
        req.setEnabled(true);

        when(configMapper.findById("non-existent")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.toggleConfig(req));
        assertEquals(ErrorCode.CONFIG_001.getCode(), ex.getCode());
    }

    @Test
    void toggleConfig_shouldThrowWhenEnableWithoutConnectivity() {
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId(existingConfig.getId());
        req.setEnabled(true);

        existingConfig.setConnectivityStatus(false);
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.toggleConfig(req));
        assertEquals(ErrorCode.CONFIG_002.getCode(), ex.getCode());
    }

    @Test
    void toggleConfig_shouldAllowEnableWhenConnectivityPassed() {
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId(existingConfig.getId());
        req.setEnabled(true);

        existingConfig.setConnectivityStatus(true);
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        configService.toggleConfig(req);

        verify(configMapper).updateEnabled(existingConfig.getId(), true);
    }

    @Test
    void toggleConfig_shouldAllowDisableWithoutConnectivity() {
        ConfigToggleReq req = new ConfigToggleReq();
        req.setId(existingConfig.getId());
        req.setEnabled(false);

        existingConfig.setConnectivityStatus(false);
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        configService.toggleConfig(req);

        verify(configMapper).updateEnabled(existingConfig.getId(), false);
    }

    @Test
    void getConfig_shouldThrowWhenNotFound() {
        when(configMapper.findById("non-existent")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> configService.getConfig("non-existent"));
        assertEquals(ErrorCode.CONFIG_001.getCode(), ex.getCode());
    }

    @Test
    void getConfig_shouldReturnConfig() {
        when(configMapper.findById(existingConfig.getId())).thenReturn(Optional.of(existingConfig));

        FileServerConfig result = configService.getConfig(existingConfig.getId());

        assertNotNull(result);
        assertEquals(existingConfig.getId(), result.getId());
    }
}
