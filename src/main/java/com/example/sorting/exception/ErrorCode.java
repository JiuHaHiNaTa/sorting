package com.example.sorting.exception;

public enum ErrorCode {

    PARAM_001("PARAM_001", "参数校验失败"),
    PARAM_002("PARAM_002", "请求体格式错误"),
    PARAM_003("PARAM_003", "参数绑定异常"),

    CONFIG_001("CONFIG_001", "配置不存在"),
    CONFIG_002("CONFIG_002", "连通性测试通过前禁止启用"),

    CNN_001("CNN_001", "AK/SK 认证失败"),
    CNN_002("CNN_002", "存储桶不存在"),
    CNN_003("CNN_003", "服务器连接异常"),

    SYS_001("SYS_001", "未知服务端异常");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
