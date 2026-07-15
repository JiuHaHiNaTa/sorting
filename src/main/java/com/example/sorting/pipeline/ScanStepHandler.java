package com.example.sorting.pipeline;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
public class ScanStepHandler implements StepHandler {

    @Autowired
    private FileService fileService;
    @Autowired
    private ConfigMapper configMapper;

    @Override
    public String getStepName() {
        return "scan";
    }

    @Override
    public StepResult execute(StepContext context) {
        String fileServerId = context.getTask().getFileServerId();
        FileServerConfig config = configMapper.findById(fileServerId)
                .orElse(null);
        if (config == null) {
            return StepResult.failed("文件服务器配置不存在: " + fileServerId);
        }

        String directory = config.getFileDirectory();
        // 核心约束：检查目录是否存在，不存在就报错
        try {
            boolean exists = fileService.checkDirectoryExists(config, directory);
            if (!exists) {
                return StepResult.failed("MinIO 目录不存在: " + directory);
            }
        } catch (BusinessException e) {
            return StepResult.failed("检查目录失败: " + e.getMessage());
        }

        try {
            List<String> files = fileService.listFiles(config, directory);
            // 只筛选 .zip 文件
            List<String> zipFiles = files.stream()
                    .filter(f -> f.toLowerCase().endsWith(".zip"))
                    .toList();
            context.setAttribute(StepContext.KEY_FILE_LIST, zipFiles);
            return StepResult.ok("扫描到 " + zipFiles.size() + " 个待处理 ZIP 文件");
        } catch (BusinessException e) {
            return StepResult.failed("扫描文件目录失败: " + e.getMessage());
        }
    }

    @Override
    public void rollback(StepContext context) {
        context.setAttribute(StepContext.KEY_FILE_LIST, null);
    }
}
