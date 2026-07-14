package com.example.sorting.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ValidateStepHandler implements StepHandler {

    private static final Pattern FILE_NAME_PATTERN =
        Pattern.compile("^[A-Za-z0-9]+_[A-Za-z0-9]+_\\d{12}_\\d{12}_[A-Za-z0-9]+\\.zip$");

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

        List<String> validFiles = new ArrayList<>();
        List<String> invalidFiles = new ArrayList<>();

        for (String fileName : fileList) {
            if (FILE_NAME_PATTERN.matcher(fileName).matches()) {
                validFiles.add(fileName);
            } else {
                invalidFiles.add(fileName);
            }
        }

        context.setAttribute(StepContext.KEY_VALID_FILES, validFiles);

        String msg = "校验完成: 合法 " + validFiles.size() + " 个, 非法 " + invalidFiles.size() + " 个";
        if (!invalidFiles.isEmpty()) {
            msg += " (非法文件: " + String.join(", ", invalidFiles) + ")";
        }
        return StepResult.ok(msg);
    }
}
