package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public class ConnectionCheckReq {

    @NotBlank(message = "配置ID不能为空")
    private String id;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
