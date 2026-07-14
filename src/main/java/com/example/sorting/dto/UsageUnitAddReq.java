package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class UsageUnitAddReq {
    @NotBlank(message = "使用量单位 code 不能为空")
    private String code;
    @NotBlank(message = "使用量单位名称不能为空")
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
