package com.example.sorting.service;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.repository.ConfigMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.MinioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

    @Mock
    private ConfigMapper configMapper;

    @InjectMocks
    private ConnectionService connectionService;

    private FileServerConfig config;

    @BeforeEach
    void setUp() {
        config = new FileServerConfig();
        config.setId(UUID.randomUUID().toString());
        config.setServerAddress("192.168.1.100");
        config.setServerPort("9000");
        config.setBucketName("test-bucket");
        config.setAccessKey("test-ak");
        config.setSecretKey("test-sk");
        config.setFileDirectory("/data/cdr");
        config.setConnectivityStatus(false);
        config.setEnabled(false);
    }

    @Test
    void checkConnection_shouldThrowWhenConfigNotFound() {
        when(configMapper.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> connectionService.checkConnection("non-existent"));
    }

    @Test
    void checkConnection_shouldReturnSuccessOnValidConnection() throws Exception {
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        when(mockClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        ApiResponse<?> response = spyService.checkConnection(config.getId());

        assertEquals("SUCCESS", response.getCode());
        verify(configMapper).updateConnectivityStatus(config.getId(), true);
    }

    @Test
    void checkConnection_shouldReturnConn002WhenBucketNotFound() throws Exception {
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        when(mockClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        ApiResponse<?> response = spyService.checkConnection(config.getId());

        assertEquals("CNN_002", response.getCode());
        verify(configMapper, never()).updateConnectivityStatus(any(), any());
    }

    @Test
    void checkConnection_shouldReturnConn001OnMinioException() throws Exception {
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        // InsufficientDataException 是 MinioException 的子类，且声明在 bucketExists 的 throws 子句中
        InsufficientDataException authException = new InsufficientDataException("Invalid access key");
        when(mockClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(authException);
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        ApiResponse<?> response = spyService.checkConnection(config.getId());

        assertEquals("CNN_001", response.getCode());
        verify(configMapper, never()).updateConnectivityStatus(any(), any());
    }

    @Test
    void checkConnection_shouldReturnConn003OnNetworkError() throws Exception {
        when(configMapper.findById(config.getId())).thenReturn(Optional.of(config));

        ConnectionService spyService = spy(connectionService);
        MinioClient mockClient = mock(MinioClient.class);
        when(mockClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        doReturn(mockClient).when(spyService).createMinioClient(any(FileServerConfig.class));

        ApiResponse<?> response = spyService.checkConnection(config.getId());

        assertEquals("CNN_003", response.getCode());
        verify(configMapper, never()).updateConnectivityStatus(any(), any());
    }
}
