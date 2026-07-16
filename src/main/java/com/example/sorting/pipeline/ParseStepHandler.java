package com.example.sorting.pipeline;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.CdrErrorRecord;
import com.example.sorting.entity.CdrRecord;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.repository.CdrErrorRecordMapper;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
    @Autowired
    private FileService fileService;
    @Autowired
    private ConfigMapper configMapper;
    @Autowired
    private CdrErrorRecordMapper errorRecordMapper;

    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private static final int MAX_ROWS = 100;

    @Override
    public String getStepName() {
        return "parse";
    }

    @Override
    public StepResult execute(StepContext context) {
        List<String> extractedFiles = context.getAttribute(StepContext.KEY_EXTRACTED_FILES);
        if (extractedFiles == null || extractedFiles.isEmpty()) {
            return StepResult.ok("没有待解析的文件");
        }

        String fileServerId = context.getTask().getFileServerId();
        FileServerConfig config = configMapper.findById(fileServerId).orElse(null);
        if (config == null) {
            return StepResult.failed("文件服务器配置不存在: " + fileServerId);
        }
        String sourceDir = config.getFileDirectory();

        List<CdrRecord> allRecords = new ArrayList<>();
        int totalRows = 0;
        int discardedRows = 0;
        List<String> parseFailedFiles = new ArrayList<>();

        for (String csvPath : extractedFiles) {
            // 从 context 获取 CSV 对应 ZIP 的文件名以构建下载路径
            // extractedFiles 格式: zipName/csvEntryName
            String[] parts = csvPath.split("/", 2);
            String zipName = parts[0] + ".zip";
            String csvEntry = parts.length > 1 ? parts[1] : csvPath;

            Path localCsvPath = null;
            try {
                // 下载原始 ZIP 再次解压提取特定 CSV（简化：先下载整个 ZIP 到临时目录处理）
                // 实际生产环境可能先在 extract 阶段解压到本地，此处直接读取已下载的文件
                // 将 ZIP 下载到本地，再解压提取
                localCsvPath = fileService.downloadFile(config, sourceDir + "/" + zipName);
                // 解压 ZIP 并读取特定 CSV
                Path extractedDir = extractAndReadCsv(localCsvPath, csvEntry);

                if (extractedDir == null) {
                    parseFailedFiles.add(csvPath);
                    continue;
                }

                Path csvFile = extractedDir.resolve(csvEntry);
                if (!csvFile.toFile().exists()) {
                    parseFailedFiles.add(csvPath);
                    continue;
                }

                // 解析 CSV 文件
                List<String> lines = Files.readAllLines(csvFile);
                if (lines.isEmpty()) {
                    continue;
                }

                // 跳过表头
                List<String> dataLines = new ArrayList<>();
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.isEmpty()) {
                        dataLines.add(line);
                    }
                }

                // 校验行数
                if (dataLines.size() > MAX_ROWS) {
                    CdrErrorRecord err = new CdrErrorRecord();
                    err.setId(UUID.randomUUID().toString());
                    err.setTaskId(context.getTaskId());
                    err.setFileName(zipName + "/" + csvEntry);
                    err.setErrorType("PARSE_FAILED");
                    err.setErrorReason("CSV 数据行数 " + dataLines.size() + " 超过限制 " + MAX_ROWS);
                    err.setCreatedAt(LocalDateTime.now());
                    errorRecordMapper.insert(err);
                    continue;
                }

                // 解析每行数据：| 运营商名称 | 服务 az | 服务资源 id | 开始时间戳 | 结束时间戳 | 使用量 | 使用量单位 |
                for (String line : dataLines) {
                    totalRows++;
                    String[] fields = line.split("\\|");
                    // 清理前后空格
                    for (int i = 0; i < fields.length; i++) {
                        fields[i] = fields[i].trim();
                    }

                    // 需要至少 7 个字段
                    if (fields.length < 7) {
                        discardedRows++;
                        continue;
                    }

                    String operatorName = fields[0];
                    String azCode = fields[1];
                    String resourceId = fields[2];
                    String startTimeStr = fields[3];
                    String endTimeStr = fields[4];
                    String usageAmountStr = fields[5];
                    String unitCode = fields[6];

                    // 验证并转换
                    Operator op = masterDataCache.getOperatorByCode(operatorName);
                    ServiceAz az = masterDataCache.getAzByCode(azCode);
                    UsageUnit unit = masterDataCache.getUnitByCode(unitCode);

                    if (op == null || az == null || unit == null) {
                        discardedRows++;
                        continue;
                    }
                    if (!UUID_PATTERN.matcher(resourceId).matches()) {
                        discardedRows++;
                        continue;
                    }

                    BigDecimal usageAmount;
                    try {
                        usageAmount = new BigDecimal(usageAmountStr);
                    } catch (NumberFormatException e) {
                        discardedRows++;
                        continue;
                    }
                    if (usageAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        discardedRows++;
                        continue;
                    }

                    CdrRecord record = new CdrRecord();
                    record.setId(UUID.randomUUID().toString());
                    record.setTaskId(context.getTaskId());
                    record.setOperatorId(op.getId());
                    record.setServiceAzId(az.getId());
                    record.setResourceId(resourceId);
                    record.setUsageAmount(usageAmount);
                    record.setUsageUnitId(unit.getId());
                    record.setStartTime(LocalDateTime.ofEpochSecond(Long.parseLong(startTimeStr), 0, ZoneOffset.UTC));
                    record.setEndTime(LocalDateTime.ofEpochSecond(Long.parseLong(endTimeStr), 0, ZoneOffset.UTC));
                    record.setCreatedAt(LocalDateTime.now());
                    allRecords.add(record);
                }

                // 清理临时文件
                cleanupDir(extractedDir);

            } catch (Exception e) {
                parseFailedFiles.add(csvPath);
            } finally {
                if (localCsvPath != null) {
                    try {
                        Files.deleteIfExists(localCsvPath);
                        Path parent = localCsvPath.getParent();
                        if (parent != null && parent.toFile().getName().startsWith("sorting-")) {
                            try (var ds = Files.list(parent)) { ds.forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); }
                            Files.deleteIfExists(parent);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        context.setAttribute(StepContext.KEY_CDR_RECORDS, allRecords);

        // 对于全行丢弃的 CSV，记录异常
        if (allRecords.isEmpty() && totalRows > 0) {
            for (String csvPath : extractedFiles) {
                if (!parseFailedFiles.contains(csvPath)) {
                    CdrErrorRecord err = new CdrErrorRecord();
                    err.setId(UUID.randomUUID().toString());
                    err.setTaskId(context.getTaskId());
                    err.setFileName(csvPath);
                    err.setErrorType("PARSE_FAILED");
                    err.setErrorReason("所有 " + totalRows + " 行数据均校验失败被丢弃");
                    err.setCreatedAt(LocalDateTime.now());
                    errorRecordMapper.insert(err);
                }
            }
        }

        String msg = String.format("解析完成: 总行数 %d, 有效 %d, 丢弃 %d",
            totalRows, allRecords.size(), discardedRows);
        return StepResult.ok(msg);
    }

    private Path extractAndReadCsv(Path zipPath, String csvEntry) throws IOException {
        Path tempDir = Files.createTempDirectory("csv-parse-");
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipPath.toFile()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().equals(csvEntry)) {
                    Path target = tempDir.resolve(entry.getName());
                    Files.createDirectories(target.getParent());
                    java.nio.file.Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return tempDir;
                }
            }
        }
        // CSV entry 未找到
        cleanupDir(tempDir);
        return null;
    }

    private void cleanupDir(Path dir) {
        if (dir == null) return;
        try {
            try (var files = Files.list(dir)) {
                files.forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
            Files.deleteIfExists(dir);
        } catch (Exception ignored) {}
    }

    @Override
    public void rollback(StepContext context) {
        context.setAttribute(StepContext.KEY_CDR_RECORDS, null);
    }
}
