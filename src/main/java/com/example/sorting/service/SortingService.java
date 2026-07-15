package com.example.sorting.service;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.entity.SortingStepLog;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.pipeline.PipelineExecutor;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.repository.SortingStepLogMapper;
import com.example.sorting.repository.SortingTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 分拣任务编排服务
 */
@Service
public class SortingService {

    @Autowired
    private ConfigMapper configMapper;
    @Autowired
    private SortingTaskMapper taskMapper;
    @Autowired
    private SortingStepLogMapper stepLogMapper;
    @Autowired
    private PipelineExecutor pipelineExecutor;
    @Autowired(required = false)
    private CdrPushService cdrPushService;

    /**
     * 触发分拣：扫描所有 enabled=true + connectivityStatus=true 的文件服务器
     */
    @Transactional
    public int trigger() {
        List<FileServerConfig> servers = configMapper.findAll();
        List<FileServerConfig> candidates = servers.stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .filter(s -> Boolean.TRUE.equals(s.getConnectivityStatus()))
                .toList();

        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.SORT_002);
        }

        int created = 0;
        for (FileServerConfig server : candidates) {
            SortingTask task = new SortingTask();
            task.setId(UUID.randomUUID().toString());
            task.setFileServerId(server.getId());
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.insert(task);

            pipelineExecutor.startTask(task);
            created++;
        }

        // 分拣任务创建后，触发推送调度（推送由 CdrPushService 定时任务处理）
        if (cdrPushService != null) {
            cdrPushService.pushPendingRecords();
        }

        return created;
    }

    public List<SortingTask> list(String status) {
        if (status != null && !status.isEmpty()) {
            return taskMapper.selectByStatus(status);
        }
        return taskMapper.selectAll();
    }

    @Transactional
    public void retry(String id) {
        SortingTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.SORT_001);
        }
        if (!"FAILED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.SORT_003);
        }
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setErrorMessage(null);
        task.setCurrentStep(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateStatus(task.getId(), "PENDING");
        taskMapper.updateErrorMessage(task.getId(), null);

        // 重新提交任务
        pipelineExecutor.startTask(taskMapper.selectById(id));
    }

    public SortingTask detail(String id) {
        SortingTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.SORT_001);
        }
        return task;
    }

    public List<SortingStepLog> stepLogs(String taskId) {
        return stepLogMapper.selectByTaskId(taskId);
    }
}
