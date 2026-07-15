package com.example.sorting.pipeline;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
public class ScanStepHandler implements StepHandler {

    @Override
    public String getStepName() {
        return "scan";
    }

    @Override
    public StepResult execute(StepContext context) {
        // 在开发环境中，先使用模拟方式扫描
        // 生产环境将通过 MinIO client 扫描文件目录
        List<String> mockFiles = List.of(
            "ColoCloud_az1_202607120800_202607120930_asia.zip",
            "ColoCloud_az1_202607120930_202607121100_asia.zip"
        );
        context.setAttribute(StepContext.KEY_FILE_LIST, mockFiles);
        return StepResult.ok("扫描到 " + mockFiles.size() + " 个待处理文件");
    }

    @Override
    public void rollback(StepContext context) {
        context.setAttribute(StepContext.KEY_FILE_LIST, null);
    }
}
