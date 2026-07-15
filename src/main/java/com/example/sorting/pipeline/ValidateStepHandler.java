package com.example.sorting.pipeline;

import com.example.sorting.entity.CdrErrorRecord;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.CdrErrorRecordMapper;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(2)
public class ValidateStepHandler implements StepHandler {

    private static final Pattern FILE_NAME_PATTERN =
        Pattern.compile("^[A-Za-z0-9]+_[A-Za-z0-9]+_\\d{12}_\\d{12}_[A-Za-z0-9]+\\.zip$");

    @Autowired
    private FileService fileService;
    @Autowired
    private ConfigMapper configMapper;
    @Autowired
    private CdrErrorRecordMapper errorRecordMapper;

    @Override
    public String getStepName() {
        return "validate";
    }

    @Override
    public StepResult execute(StepContext context) {
        List<String> fileList = context.getAttribute(StepContext.KEY_FILE_LIST);
        if (fileList == null || fileList.isEmpty()) {
            return StepResult.failed("没有待校验的文件");
        }

        String fileServerId = context.getTask().getFileServerId();
        FileServerConfig config = configMapper.findById(fileServerId).orElse(null);

        List<String> validFiles = new ArrayList<>();
        List<String> errorFiles = new ArrayList<>();

        for (String fileName : fileList) {
            if (FILE_NAME_PATTERN.matcher(fileName).matches()) {
                validFiles.add(fileName);
            } else {
                errorFiles.add(fileName);
                // 将非法文件移到 /error/{yyyyMMdd}/
                if (config != null) {
                    String sourceDir = config.getFileDirectory();
                    try {
                        String targetPath = fileService.buildArchivePath("/error", "yyyyMMdd", fileName);
                        fileService.moveFile(config, sourceDir + "/" + fileName, targetPath);

                        // 记录异常
                        CdrErrorRecord err = new CdrErrorRecord();
                        err.setId(UUID.randomUUID().toString());
                        err.setTaskId(context.getTaskId());
                        err.setFileName(fileName);
                        err.setErrorType("FILE_NAME_INVALID");
                        err.setErrorReason("文件名不匹配规范: " + FILE_NAME_PATTERN.pattern());
                        err.setCreatedAt(LocalDateTime.now());
                        errorRecordMapper.insert(err);
                    } catch (Exception e) {
                        // 记录异常但继续处理其他文件
                        CdrErrorRecord err = new CdrErrorRecord();
                        err.setId(UUID.randomUUID().toString());
                        err.setTaskId(context.getTaskId());
                        err.setFileName(fileName);
                        err.setErrorType("FILE_NAME_INVALID");
                        err.setErrorReason("校验失败，无法移动到错误目录: " + e.getMessage());
                        err.setCreatedAt(LocalDateTime.now());
                        errorRecordMapper.insert(err);
                    }
                }
            }
        }

        context.setAttribute(StepContext.KEY_VALID_FILES, validFiles);
        context.setAttribute(StepContext.KEY_ERROR_FILES, errorFiles);

        String msg = "校验完成: 合法 " + validFiles.size() + " 个, 非法 " + errorFiles.size() + " 个";
        return StepResult.ok(msg);
    }

    @Override
    public void rollback(StepContext context) {
        context.setAttribute(StepContext.KEY_VALID_FILES, null);
        context.setAttribute(StepContext.KEY_ERROR_FILES, null);
    }
}
