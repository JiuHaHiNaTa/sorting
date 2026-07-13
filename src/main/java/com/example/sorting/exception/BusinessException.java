package com.example.sorting.exception;

public class BusinessException extends RuntimeException {

    private final String code;
    private final String message;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.message = detail;
    }

    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
