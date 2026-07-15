package com.example.sorting.pipeline;

import com.example.sorting.entity.CdrErrorRecord;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.CdrErrorRecordMapper;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@Order(3)
public class ExtractStepHandler implements StepHandler {

    private static final long MAX_TOTAL_SIZE = 100L * 1024 * 1024; // 100MB
    private static final int MAX_FILE_COUNT = 100;

    @Autowired
    private FileService fileService;
    @Autowired
    private ConfigMapper configMapper;
    @Autowired
    private CdrErrorRecordMapper errorRecordMapper;

    @Override
    public String getStepName() {
        return "extract";
    }

    @Override
    public StepResult execute(StepContext context) {
        List<String> validFiles = context.getAttribute(StepContext.KEY_VALID_FILES);
        if (validFiles == null || validFiles.isEmpty()) {
            return StepResult.ok("没有待解压的文件");
        }

        String fileServerId = context.getTask().getFileServerId();
        FileServerConfig config = configMapper.findById(fileServerId).orElse(null);
        if (config == null) {
            return StepResult.failed("文件服务器配置不存在");
        }

        String sourceDir = config.getFileDirectory();
        List<String> extractedCsvFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (String zipFile : validFiles) {
            Path localZipPath = null;
            try {
                // 下载 ZIP 到本地
                localZipPath = fileService.downloadFile(config, sourceDir + "/" + zipFile);

                // 解压并校验
                List<String> csvEntries = new ArrayList<>();
                long totalCsvSize = 0;
                int csvCount = 0;

                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(localZipPath.toFile()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory()) continue;
                        // 只关注 CSV 文件
                        if (entry.getName().toLowerCase().endsWith(".csv")) {
                            csvCount++;
                            long entrySize = entry.getSize();
                            if (entrySize < 0) {
                                // 数据描述符模式，getSize 不可靠；读取并计数字节
                                byte[] buf = new byte[8192];
                                int read;
                                while ((read = zis.read(buf)) != -1) {
                                    entrySize += read;
                                }
                            }
                            totalCsvSize += entrySize;
                            csvEntries.add(entry.getName());
                        }
                    }
                }

                // 校验规则
                boolean valid = true;
                StringBuilder failReason = new StringBuilder();

                if (csvCount > MAX_FILE_COUNT) {
                    valid = false;
                    failReason.append("CSV 文件数 ").append(csvCount).append(" 超过限制 ").append(MAX_FILE_COUNT).append("; ");
                }
                if (totalCsvSize > MAX_TOTAL_SIZE) {
                    valid = false;
                    failReason.append("CSV 总大小 ").append(totalCsvSize).append(" 超过限制 ").append(MAX_TOTAL_SIZE).append("; ");
                }

                if (!valid) {
                    failedFiles.add(zipFile);
                    // 记录异常并移动文件到 /error
                    CdrErrorRecord err = new CdrErrorRecord();
                    err.setId(UUID.randomUUID().toString());
                    err.setTaskId(context.getTaskId());
                    err.setFileName(zipFile);
                    err.setErrorType("ZIP_CORRUPTED");
                    err.setErrorReason(failReason.toString());
                    err.setCreatedAt(LocalDateTime.now());
                    errorRecordMapper.insert(err);

                    String targetPath = fileService.buildArchivePath("/error", "yyyyMMdd", zipFile);
                    fileService.moveFile(config, sourceDir + "/" + zipFile, targetPath);
                } else {
                    // 为每个 CSV 构建本地文件引用
                    for (String csvEntry : csvEntries) {
                        extractedCsvFiles.add(zipFile.replace(".zip", "/" + csvEntry));
                    }
                }

            } catch (Exception e) {
                failedFiles.add(zipFile);
                CdrErrorRecord err = new CdrErrorRecord();
                err.setId(UUID.randomUUID().toString());
                err.setTaskId(context.getTaskId());
                err.setFileName(zipFile);
                err.setErrorType("ZIP_CORRUPTED");
                err.setErrorReason("解压失败: " + e.getMessage());
                err.setCreatedAt(LocalDateTime.now());
                errorRecordMapper.insert(err);

                try {
                    String targetPath = fileService.buildArchivePath("/error", "yyyyMMdd", zipFile);
                    fileService.moveFile(config, sourceDir + "/" + zipFile, targetPath);
                } catch (Exception ignored) {
                    // 移动失败不阻断
                }
            } finally {
                // 清理本地临时 ZIP 文件
                if (localZipPath != null) {
                    try {
                        Files.deleteIfExists(localZipPath);
                        // 删除可能创建的临时目录（及其父目录）
                        Path parent = localZipPath.getParent();
                        if (parent != null && parent.toFile().getName().startsWith("sorting-")) {
                            try (var dirStream = Files.list(parent)) {
                                dirStream.forEach(p -> {
                                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                                });
                            }
                            Files.deleteIfExists(parent);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        context.setAttribute(StepContext.KEY_EXTRACTED_FILES, extractedCsvFiles);

        String msg = "解压完成: 成功 " + extractedCsvFiles.size() + " 个 CSV, 失败 " + failedFiles.size() + " 个 ZIP";
        return StepResult.ok(msg);
    }

    @Override
    public void rollback(StepContext context) {
        context.setAttribute(StepContext.KEY_EXTRACTED_FILES, null);
    }
}
