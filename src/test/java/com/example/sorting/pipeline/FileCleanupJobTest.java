package com.example.sorting.pipeline;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.CdrErrorRecordMapper;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileCleanupJobTest {

    @Mock private FileService fileService;
    @Mock private ConfigMapper configMapper;
    @Mock private CdrErrorRecordMapper errorRecordMapper;
    @InjectMocks private FileCleanupJob cleanupJob;

    @Test
    void cleanupOldFiles_shouldHandleEmptyServers() {
        when(configMapper.findAll()).thenReturn(List.of());
        cleanupJob.cleanupOldFiles();
        verify(configMapper).findAll();
        verifyNoMoreInteractions(fileService);
    }

    @Test
    void cleanupOldFiles_shouldProcessServerWithNoSubdirs() {
        FileServerConfig config = new FileServerConfig();
        config.setId("server-1");
        when(configMapper.findAll()).thenReturn(List.of(config));
        when(fileService.listFiles(any(FileServerConfig.class), eq("/backup"))).thenReturn(List.of());
        when(fileService.listFiles(any(FileServerConfig.class), eq("/error"))).thenReturn(List.of());
        cleanupJob.cleanupOldFiles();
        verify(fileService, times(2)).listFiles(any(FileServerConfig.class), anyString());
    }

    @Test
    void cleanupOldErrorRecords_shouldCallMapper() {
        cleanupJob.cleanupOldErrorRecords();
        verify(errorRecordMapper).deleteOldRecords(any());
    }
}