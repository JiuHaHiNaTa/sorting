package com.example.sorting.pipeline;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(6)
public class ArchiveStepHandler implements StepHandler {

    @Override
    public String getStepName() {
        return "archive";
    }

    @Override
    public StepResult execute(StepContext context) {
        // 模拟归档操作
        // 生产环境：将原始 ZIP 文件从源目录移到 backup/ 目录，清理本地临时目录
        return StepResult.ok("归档完成");
    }
}
