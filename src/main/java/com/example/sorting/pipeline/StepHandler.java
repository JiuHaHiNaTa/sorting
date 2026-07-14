package com.example.sorting.pipeline;

/**
 * 步骤处理器接口。
 * 每个分拣步骤实现此接口，PipelineExecutor 按注入顺序驱动执行。
 */
public interface StepHandler {

    /** 步骤名称标识，如 "scan"、"validate" */
    String getStepName();

    /** 执行步骤逻辑 */
    StepResult execute(StepContext context);

    /** 崩溃恢复时清理上一步可能的残留（可选） */
    default void rollback(StepContext context) {
        // 默认空实现
    }
}
