package com.example.sorting.pipeline;

import com.example.sorting.entity.SortingStepLog;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.entity.SortingTaskBackup;
import com.example.sorting.repository.SortingStepLogMapper;
import com.example.sorting.repository.SortingTaskMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Pipeline 编排器。
 * 驱动所有注册的 StepHandler 按顺序执行，支持重试、崩溃恢复和超时检测。
 */
@Component
public class PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);
    private static final int MAX_RETRY = 3;
    private static final int TIMEOUT_MINUTES = 5;

    @Autowired(required = false)
    private List<StepHandler> stepHandlers = Collections.emptyList();
    @Autowired
    private SortingTaskMapper taskMapper;
    @Autowired
    private SortingStepLogMapper stepLogMapper;
    @Autowired
    @Qualifier("sortingTaskPool")
    private ThreadPoolTaskExecutor executor;

    /**
     * 启动一个分拣任务（异步）
     */
    public void startTask(SortingTask task) {
        executor.submit(() -> executeTask(task));
    }

    private void executeTask(SortingTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        task.setTimeoutAt(LocalDateTime.now().plusMinutes(TIMEOUT_MINUTES));
        taskMapper.updateStatus(task.getId(), "RUNNING");

        StepContext context = new StepContext(task);

        for (StepHandler handler : stepHandlers) {
            task.setCurrentStep(handler.getStepName());
            taskMapper.updateStep(task.getId(), handler.getStepName());

            boolean stepSuccess = false;
            String errorMsg = null;

            for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
                if (attempt > 0) {
                    log.info("重试步骤 [{}] 任务 [{}], 第 {}/{} 次", handler.getStepName(), task.getId(), attempt, MAX_RETRY);
                    handler.rollback(context);
                }

                SortingStepLog stepLog = new SortingStepLog();
                stepLog.setId(UUID.randomUUID().toString());
                stepLog.setTaskId(task.getId());
                stepLog.setStepName(handler.getStepName());
                stepLog.setStartedAt(LocalDateTime.now());

                try {
                    StepResult result = handler.execute(context);
                    if (result.isSuccess()) {
                        stepLog.setStatus("SUCCESS");
                        stepLog.setCompletedAt(LocalDateTime.now());
                        stepLog.setDetail(result.getMessage());
                        stepLogMapper.insert(stepLog);
                        stepSuccess = true;
                        break;
                    } else {
                        stepLog.setStatus("FAILED");
                        stepLog.setCompletedAt(LocalDateTime.now());
                        stepLog.setDetail(result.getMessage());
                        stepLogMapper.insert(stepLog);
                        errorMsg = result.getMessage();
                    }
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                    log.error("步骤 [{}] 任务 [{}] 失败: {}", handler.getStepName(), task.getId(), errorMsg);
                    stepLog.setStatus("FAILED");
                    stepLog.setCompletedAt(LocalDateTime.now());
                    stepLog.setDetail(errorMsg);
                    stepLogMapper.insert(stepLog);
                }
            }

            if (!stepSuccess) {
                task.setErrorMessage(errorMsg);
                taskMapper.updateErrorMessage(task.getId(), errorMsg);
                taskMapper.updateStatus(task.getId(), "FAILED");
                log.error("任务 [{}] 在步骤 [{}] 经重试后失败", task.getId(), handler.getStepName());
                return;
            }
        }

        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateStatus(task.getId(), "COMPLETED");

        archiveTask(task);
    }

    private void archiveTask(SortingTask task) {
        SortingTaskBackup backup = new SortingTaskBackup();
        backup.setId(task.getId());
        backup.setFileServerId(task.getFileServerId());
        backup.setStatus(task.getStatus());
        backup.setCurrentStep(task.getCurrentStep());
        backup.setRetryCount(task.getRetryCount());
        backup.setErrorMessage(task.getErrorMessage());
        backup.setStartedAt(task.getStartedAt());
        backup.setCompletedAt(task.getCompletedAt());
        backup.setTimeoutAt(task.getTimeoutAt());
        backup.setArchivedAt(LocalDateTime.now());
        taskMapper.insertBackup(backup);
        taskMapper.deleteById(task.getId());
        log.info("任务 [{}] 已归档到备份表", task.getId());
    }

    /**
     * 崩溃恢复：服务启动时自动执行。
     * 查询所有 RUNNING 状态的任务，重新提交执行。
     */
    @PostConstruct
    public void recoverInterruptedTasks() {
        List<SortingTask> runningTasks = taskMapper.selectByStatus("RUNNING");
        if (!runningTasks.isEmpty()) {
            log.info("恢复 {} 个中断任务", runningTasks.size());
            for (SortingTask task : runningTasks) {
                log.info("恢复中断任务 [{}] 在步骤 [{}]", task.getId(), task.getCurrentStep());
                task.setTimeoutAt(LocalDateTime.now().plusMinutes(TIMEOUT_MINUTES));
                startTask(task);
            }
        }
    }

    /**
     * 超时监控：每 30 秒检测一次。
     * 查询超时任务，标记为 FAILED。
     */
    @Scheduled(fixedRate = 30_000)
    public void timeoutCheck() {
        List<SortingTask> timeoutTasks = taskMapper.selectRunningTimeoutTasks(LocalDateTime.now());
        for (SortingTask task : timeoutTasks) {
            log.warn("任务 [{}] 超时，标记为 FAILED", task.getId());
            task.setStatus("FAILED");
            task.setErrorMessage("任务超时（" + TIMEOUT_MINUTES + " 分钟）");
            taskMapper.updateStatus(task.getId(), "FAILED");
            taskMapper.updateErrorMessage(task.getId(), task.getErrorMessage());
        }
    }
}
