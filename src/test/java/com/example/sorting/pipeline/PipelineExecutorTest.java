package com.example.sorting.pipeline;

import com.example.sorting.entity.SortingTask;
import com.example.sorting.repository.SortingStepLogMapper;
import com.example.sorting.repository.SortingTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PipelineExecutor 单元测试。
 * 使用 Mockito 纯单元测试，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class PipelineExecutorTest {

    @Mock private SortingTaskMapper taskMapper;
    @Mock private SortingStepLogMapper stepLogMapper;
    @Mock(name = "sortingTaskPool") private ThreadPoolTaskExecutor executor;
    @Mock private StepHandler stepHandler;
    @InjectMocks private PipelineExecutor pipelineExecutor;

    @Test
    void recoverInterruptedTasks_shouldDoNothingWhenNoRunningTasks() {
        when(taskMapper.selectByStatus("RUNNING")).thenReturn(Collections.emptyList());
        pipelineExecutor.recoverInterruptedTasks();
        verify(taskMapper).selectByStatus("RUNNING");
        verifyNoMoreInteractions(taskMapper);
    }

    @Test
    void recoverInterruptedTasks_shouldProcessRunningTasks() {
        SortingTask task = new SortingTask();
        task.setId("task-1");
        task.setFileServerId("fs-1");
        task.setStatus("RUNNING");
        task.setCurrentStep("extract");
        when(taskMapper.selectByStatus("RUNNING")).thenReturn(List.of(task));

        // 注入 StepHandler mock，避免 executeTask 中遍历时 NPE
        ReflectionTestUtils.setField(pipelineExecutor, "stepHandlers", List.of(stepHandler));

        when(stepHandler.getStepName()).thenReturn("extract");
        when(stepHandler.execute(any(StepContext.class))).thenReturn(StepResult.ok());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executor).submit(any(Runnable.class));

        pipelineExecutor.recoverInterruptedTasks();

        verify(taskMapper).selectByStatus("RUNNING");
        verify(stepHandler).execute(any(StepContext.class));
    }

    @Test
    void timeoutCheck_shouldMarkTimeoutTasks() {
        SortingTask timeoutTask = new SortingTask();
        timeoutTask.setId("timeout-1");
        when(taskMapper.selectRunningTimeoutTasks(any(LocalDateTime.class)))
            .thenReturn(List.of(timeoutTask));

        pipelineExecutor.timeoutCheck();

        verify(taskMapper).updateStatus("timeout-1", "FAILED");
        verify(taskMapper).updateErrorMessage(eq("timeout-1"), anyString());
    }
}
