package com.example.sorting.pipeline;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(6)
public class ArchiveStepHandler implements StepHandler {

    @Autowired
    private FileService fileService;
    @Autowired
    private ConfigMapper configMapper;

    @Override
    public String getStepName() {
        return "archive";
    }

    @Override
    public StepResult execute(StepContext context) {
        // 获取原始文件列表
        List<String> fileList = context.getAttribute(StepContext.KEY_FILE_LIST);
        if (fileList == null || fileList.isEmpty()) {
            return StepResult.ok("没有需要归档的文件");
        }

        String fileServerId = context.getTask().getFileServerId();
        FileServerConfig config = configMapper.findById(fileServerId).orElse(null);
        if (config == null) {
            return StepResult.failed("文件服务器配置不存在");
        }

        String sourceDir = config.getFileDirectory();

        int archived = 0;
        int failed = 0;
        for (String fileName : fileList) {
            try {
                String targetPath = fileService.buildArchivePath("/backup", "yyyyMMdd", fileName);
                fileService.moveFile(config, sourceDir + "/" + fileName, targetPath);
                archived++;
            } catch (Exception e) {
                failed++;
            }
        }

        return StepResult.ok("归档完成: 成功 " + archived + " 个, 失败 " + failed + " 个");
    }
}
