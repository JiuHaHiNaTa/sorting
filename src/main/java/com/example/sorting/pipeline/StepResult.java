package com.example.sorting.pipeline;

/**
 * 步骤执行结果，不可变对象。
 * 使用静态工厂方法创建：{@link #ok()}、{@link #ok(String)}、{@link #failed(String)}。
 */
public class StepResult {
    private final boolean success;
    private final String message;

    private StepResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static StepResult ok() {
        return new StepResult(true, null);
    }

    public static StepResult ok(String message) {
        return new StepResult(true, message);
    }

    public static StepResult failed(String message) {
        return new StepResult(false, message);
    }

    public boolean isSuccess() { return success; }
    public boolean isFailed() { return !success; }
    public String getMessage() { return message; }
}
