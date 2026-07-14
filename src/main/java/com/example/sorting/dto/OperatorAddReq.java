package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class OperatorAddReq {
    @NotBlank(message = "运营商 code 不能为空")
    private String code;
    @NotBlank(message = "运营商名称不能为空")
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
