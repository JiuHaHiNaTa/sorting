package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class UsageUnitModifyReq {
    @NotBlank(message = "id 不能为空")
    private String id;
    private String code;
    private String name;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
