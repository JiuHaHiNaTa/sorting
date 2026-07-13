package com.example.sorting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ConfigToggleReq {

    @NotBlank(message = "配置ID不能为空")
    private String id;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
