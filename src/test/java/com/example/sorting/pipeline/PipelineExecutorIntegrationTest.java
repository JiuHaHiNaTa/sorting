package com.example.sorting.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PipelineExecutor 集成测试。
 * 验证 Spring 容器正确注入所有 6 个 StepHandler，且执行顺序与 @Order 一致。
 */
@SpringBootTest
class PipelineExecutorIntegrationTest {

    @Autowired
    private PipelineExecutor pipelineExecutor;

    @Test
    void stepHandlers_shouldContainAllSixHandlers() {
        @SuppressWarnings("unchecked")
        List<StepHandler> handlers = (List<StepHandler>)
            ReflectionTestUtils.getField(pipelineExecutor, "stepHandlers");

        assertNotNull(handlers, "stepHandlers 不应为 null");
        assertEquals(6, handlers.size(), "应注入 6 个 StepHandler");

        // 验证步骤顺序与 @Order(1) ~ @Order(6) 一致
        String[] expectedOrder = {"scan", "validate", "extract", "parse", "persist", "archive"};
        for (int i = 0; i < expectedOrder.length; i++) {
            assertEquals(expectedOrder[i], handlers.get(i).getStepName(),
                "步骤 " + (i + 1) + " 应为 " + expectedOrder[i]);
        }
    }
}
