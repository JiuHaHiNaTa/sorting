package com.example.sorting.dto;

/**
 * 连通性测试的内部结果包装，用于 Service 层内部传递测试结果。
 */
public class ConnectionResult {
    private final boolean success;
    private final String code;
    private final String message;

    private ConnectionResult(boolean success, String code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }

    public static ConnectionResult success() {
        return new ConnectionResult(true, "SUCCESS", "连通性测试通过");
    }

    public static ConnectionResult failed(String code, String message) {
        return new ConnectionResult(false, code, message);
    }

    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
