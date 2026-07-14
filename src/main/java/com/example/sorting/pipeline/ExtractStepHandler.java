package com.example.sorting.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExtractStepHandler implements StepHandler {

    @Override
    public String getStepName() {
        return "extract";
    }

    @Override
    public StepResult execute(StepContext context) {
        List<String> validFiles = context.getAttribute(StepContext.KEY_VALID_FILES);
        if (validFiles == null || validFiles.isEmpty()) {
            return StepResult.failed("没有待解压的文件");
        }

        // 模拟解压：每个 ZIP 对应一个 CSV 文件
        List<String> extractedFiles = new ArrayList<>();
        for (String zipFile : validFiles) {
            String csvFile = zipFile.replace(".zip", ".csv");
            extractedFiles.add(csvFile);
        }

        context.setAttribute(StepContext.KEY_EXTRACTED_FILES, extractedFiles);
        return StepResult.ok("解压完成，共 " + extractedFiles.size() + " 个 CSV 文件");
    }
}
