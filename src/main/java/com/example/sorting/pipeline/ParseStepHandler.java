package com.example.sorting.pipeline;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.CdrRecord;
import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.UsageUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(4)
public class ParseStepHandler implements StepHandler {

    @Autowired
    private MasterDataCache masterDataCache;

    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Override
    public String getStepName() {
        return "parse";
    }

    @Override
    public StepResult execute(StepContext context) {
        List<String> extractedFiles = context.getAttribute(StepContext.KEY_EXTRACTED_FILES);
        if (extractedFiles == null || extractedFiles.isEmpty()) {
            return StepResult.failed("没有待解析的文件");
        }

        List<CdrRecord> allRecords = new ArrayList<>();
        int totalRows = 0;
        int discardedRows = 0;

        for (String csvFile : extractedFiles) {
            // 模拟 CSV 解析
            // CSV 格式：| 运营商名称 | 服务 az | 服务资源 id | 开始时间戳 | 结束时间戳 | 使用量 | 使用量单位 |
            // 为每个 CSV 文件生成模拟数据
            List<CdrRecord> records = generateMockRecords(context.getTaskId(), csvFile);
            List<CdrRecord> validRecords = new ArrayList<>();

            for (CdrRecord record : records) {
                totalRows++;
                if (validateRecord(record)) {
                    validRecords.add(record);
                } else {
                    discardedRows++;
                }
            }

            allRecords.addAll(validRecords);
        }

        context.setAttribute(StepContext.KEY_CDR_RECORDS, allRecords);

        String msg = String.format("解析完成: 总行数 %d, 有效 %d, 丢弃 %d",
            totalRows, allRecords.size(), discardedRows);
        return StepResult.ok(msg);
    }

    private List<CdrRecord> generateMockRecords(String taskId, String csvFileName) {
        List<CdrRecord> records = new ArrayList<>();

        // 尝试从文件名解析运营商和可用区（格式：ColoCloud_az1_202607120800_202607120930.csv）
        String baseName = csvFileName.replace(".csv", "");
        String[] parts = baseName.split("_");
        String operatorCode = parts.length > 0 ? parts[0] : "ColoCloud";
        String azCode = parts.length > 1 ? parts[1] : "az1";
        long startTs = parts.length > 2 ? Long.parseLong(parts[2]) : 1720771200L;

        // 生成 3 条模拟话单记录
        for (int i = 0; i < 3; i++) {
            CdrRecord record = new CdrRecord();
            record.setId(UUID.randomUUID().toString());
            record.setTaskId(taskId);
            record.setOperatorId(findOperatorId(operatorCode));
            record.setServiceAzId(findAzId(azCode));
            record.setResourceId(UUID.randomUUID().toString());
            record.setUsageAmount(BigDecimal.valueOf(Math.random() * 100));
            record.setUsageUnitId(findUnitId("MB"));
            record.setStartTime(LocalDateTime.ofEpochSecond(startTs + i * 600, 0, ZoneOffset.UTC));
            record.setEndTime(LocalDateTime.ofEpochSecond(startTs + (i + 1) * 600, 0, ZoneOffset.UTC));
            record.setCreatedAt(LocalDateTime.now());
            records.add(record);
        }

        return records;
    }

    private boolean validateRecord(CdrRecord record) {
        return record.getOperatorId() != null
            && record.getServiceAzId() != null
            && record.getUsageUnitId() != null
            && UUID_PATTERN.matcher(record.getResourceId()).matches()
            && record.getUsageAmount() != null
            && record.getUsageAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private String findOperatorId(String code) {
        Operator op = masterDataCache.getOperatorByCode(code);
        return op != null ? op.getId() : null;
    }

    private String findAzId(String code) {
        ServiceAz az = masterDataCache.getAzByCode(code);
        return az != null ? az.getId() : null;
    }

    private String findUnitId(String code) {
        UsageUnit unit = masterDataCache.getUnitByCode(code);
        return unit != null ? unit.getId() : null;
    }
}
