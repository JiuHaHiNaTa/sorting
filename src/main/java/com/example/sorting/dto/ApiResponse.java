package com.example.sorting.dto;

public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;

    public ApiResponse() {}

    public ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "操作成功", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>("SUCCESS", "操作成功", null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
