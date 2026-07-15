package com.example.sorting.pipeline;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.repository.CdrErrorRecordMapper;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时清理任务。
 * 清理 /backup/ 和 /error/ 目录中超过保留天数的文件。
 */
@Component
public class FileCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupJob.class);

    @Autowired
    private FileService fileService;
    @Autowired
    private ConfigMapper configMapper;
    @Autowired
    private CdrErrorRecordMapper errorRecordMapper;

    @Value("${cdr.cleanup.file-retention-days:7}")
    private int fileRetentionDays;

    @Value("${cdr.cleanup.error-record-retention-days:30}")
    private int errorRecordRetentionDays;

    /**
     * 每天凌晨 2:00 清理过期文件夹。
     */
    @Scheduled(cron = "${cdr.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOldFiles() {
        List<FileServerConfig> servers = configMapper.findAll();
        LocalDate cutoff = LocalDate.now().minusDays(fileRetentionDays);
        String cutoffStr = cutoff.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (FileServerConfig server : servers) {
            cleanupDirectory(server, "/backup", cutoffStr);
            cleanupDirectory(server, "/error", cutoffStr);
        }
    }

    private void cleanupDirectory(FileServerConfig config, String baseDir, String cutoffStr) {
        try {
            // 列出 baseDir 下所有子目录
            List<String> subDirs = fileService.listFiles(config, baseDir);
            for (String subDir : subDirs) {
                String dirName = subDir.replaceAll("/", "");
                // 判断是否是日期目录且在 cutoff 之前
                if (isDateBefore(dirName, cutoffStr)) {
                    String fullPath = baseDir + "/" + dirName;
                    log.info("清理过期目录: {}", fullPath);
                    fileService.removeObject(config, fullPath);
                }
            }
        } catch (Exception e) {
            log.error("清理目录 [{}] 失败: {}", baseDir, e.getMessage());
        }
    }

    /**
     * 判断 dirName (yyyyMMdd) 是否在 cutoffStr (yyyyMMdd) 之前。
     */
    private boolean isDateBefore(String dirName, String cutoffStr) {
        if (dirName.length() != 8) return false;
        try {
            Integer.parseInt(dirName); // 验证纯数字
            return dirName.compareTo(cutoffStr) < 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 凌晨 2:30 清理 30 天前的异常记录。
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanupOldErrorRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(errorRecordRetentionDays);
        int deleted = errorRecordMapper.deleteOldRecords(cutoff);
        if (deleted > 0) {
            log.info("清理了 {} 条过期异常记录", deleted);
        }
    }
}