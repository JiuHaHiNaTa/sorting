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

    SYS_001("SYS_001", "未知服务端异常"),

    // 基础数据类
    OP_001("OP_001", "运营商不存在"),
    OP_002("OP_002", "运营商 code 已存在"),
    OP_003("OP_003", "运营商已被 cdr_record 引用，无法删除"),
    AZ_001("AZ_001", "可用区不存在"),
    AZ_002("AZ_002", "可用区 code 已存在"),
    AZ_003("AZ_003", "可用区已被 cdr_record 引用，无法删除"),
    UNIT_001("UNIT_001", "使用量单位不存在"),
    UNIT_002("UNIT_002", "使用量单位 code 已存在"),
    UNIT_003("UNIT_003", "使用量单位已被 cdr_record 引用，无法删除"),

    // 分拣类
    SORT_001("SORT_001", "分拣任务不存在"),
    SORT_002("SORT_002", "没有可执行的分拣任务"),
    SORT_003("SORT_003", "分拣任务状态不允许该操作");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
