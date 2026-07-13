package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.ConfigAddReq;
import com.example.sorting.dto.ConfigModifyReq;
import com.example.sorting.dto.ConfigToggleReq;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("/config/add")
    public ApiResponse<FileServerConfig> addConfig(@Valid @RequestBody ConfigAddReq req) {
        FileServerConfig config = configService.addConfig(req);
        return ApiResponse.success(config);
    }

    @PostMapping("/config/modify")
    public ApiResponse<FileServerConfig> modifyConfig(@Valid @RequestBody ConfigModifyReq req) {
        FileServerConfig config = configService.modifyConfig(req);
        return ApiResponse.success(config);
    }

    @PostMapping("/config/toggle")
    public ApiResponse<FileServerConfig> toggleConfig(@Valid @RequestBody ConfigToggleReq req) {
        FileServerConfig config = configService.toggleConfig(req);
        return ApiResponse.success(config);
    }
}
