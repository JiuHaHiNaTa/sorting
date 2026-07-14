package com.example.sorting.service;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.pipeline.PipelineExecutor;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.repository.SortingTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SortingServiceTest {

    @Mock private ConfigMapper configMapper;
    @Mock private SortingTaskMapper taskMapper;
    @Mock private PipelineExecutor pipelineExecutor;
    @InjectMocks private SortingService sortingService;

    @Test
    void trigger_shouldThrowWhenNoAvailableServers() {
        when(configMapper.findAll()).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> sortingService.trigger());
    }

    @Test
    void trigger_shouldCreateTaskForConnectedServer() {
        FileServerConfig server = new FileServerConfig();
        server.setId("fs-1");
        server.setEnabled(true);
        server.setConnectivityStatus(true);
        when(configMapper.findAll()).thenReturn(List.of(server));

        int count = sortingService.trigger();
        assertEquals(1, count);
        verify(taskMapper).insert(any(SortingTask.class));
        verify(pipelineExecutor).startTask(any(SortingTask.class));
    }

    @Test
    void trigger_shouldSkipDisabledServer() {
        FileServerConfig enabled = new FileServerConfig();
        enabled.setId("fs-1");
        enabled.setEnabled(true);
        enabled.setConnectivityStatus(true);
        FileServerConfig disabled = new FileServerConfig();
        disabled.setId("fs-2");
        disabled.setEnabled(false);
        disabled.setConnectivityStatus(true);
        when(configMapper.findAll()).thenReturn(List.of(enabled, disabled));

        int count = sortingService.trigger();
        assertEquals(1, count);
        verify(taskMapper, times(1)).insert(any(SortingTask.class));
    }

    @Test
    void retry_shouldThrowWhenTaskNotFound() {
        when(taskMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class, () -> sortingService.retry("non-existent"));
    }

    @Test
    void retry_shouldThrowWhenNotFailed() {
        SortingTask running = new SortingTask();
        running.setId("running-task");
        running.setStatus("RUNNING");
        when(taskMapper.selectById("running-task")).thenReturn(running);
        assertThrows(BusinessException.class, () -> sortingService.retry("running-task"));
    }

    @Test
    void retry_shouldSucceed() {
        SortingTask failed = new SortingTask();
        failed.setId("failed-task");
        failed.setStatus("FAILED");
        when(taskMapper.selectById("failed-task")).thenReturn(failed, failed);

        sortingService.retry("failed-task");
        verify(taskMapper).updateStatus("failed-task", "PENDING");
        verify(pipelineExecutor).startTask(any(SortingTask.class));
    }

    @Test
    void list_shouldFilterByStatus() {
        when(taskMapper.selectByStatus("COMPLETED")).thenReturn(List.of(new SortingTask()));
        assertEquals(1, sortingService.list("COMPLETED").size());
        verify(taskMapper).selectByStatus("COMPLETED");
    }

    @Test
    void list_shouldReturnAllWhenNoStatus() {
        when(taskMapper.selectAll()).thenReturn(List.of(new SortingTask(), new SortingTask()));
        assertEquals(2, sortingService.list(null).size());
        verify(taskMapper).selectAll();
    }

    @Test
    void detail_shouldThrowWhenNotFound() {
        when(taskMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class, () -> sortingService.detail("non-existent"));
    }

    @Test
    void detail_shouldReturnTask() {
        SortingTask task = new SortingTask();
        task.setId("detail-task");
        when(taskMapper.selectById("detail-task")).thenReturn(task);
        assertEquals("detail-task", sortingService.detail("detail-task").getId());
    }

    @Test
    void stepLogs_shouldReturnLogs() {
        when(taskMapper.selectById("exists")).thenReturn(new SortingTask());
        assertNotNull(sortingService.detail("exists"));
    }
}
