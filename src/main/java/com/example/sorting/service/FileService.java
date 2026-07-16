package com.example.sorting.service;

import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * MinIO 文件操作抽象层。
 * 封装所有与 MinIO 的交互，各 StepHandler 通过此服务操作文件。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * 根据配置创建 MinIO 客户端。
     * 包级可见，方便测试 spy。
     */
    MinioClient createClient(FileServerConfig config) {
        return MinioClient.builder()
                .endpoint(config.getServerAddress(), Integer.parseInt(config.getServerPort()), false)
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
    }

    /**
     * 检查 MinIO 上指定目录是否存在。
     * 不存在时不允许创建，直接抛出异常。
     */
    public boolean checkDirectoryExists(FileServerConfig config, String directoryPath) {
        MinioClient client = createClient(config);
        try {
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(config.getBucketName())
                            .prefix(directoryPath.endsWith("/") ? directoryPath : directoryPath + "/")
                            .maxKeys(1)
                            .build());
            for (Result<Item> result : results) {
                result.get(); // 有记录即目录存在
                return true;
            }
            return false;
        } catch (MinioException e) {
            throw new BusinessException(ErrorCode.FILE_002, "检查目录存在性失败: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_002, "检查目录存在性失败: " + e.getMessage());
        }
    }

    /** 列出目录下所有文件名 */
    public List<String> listFiles(FileServerConfig config, String directoryPath) {
        MinioClient client = createClient(config);
        List<String> fileNames = new ArrayList<>();
        try {
            String prefix = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder().bucket(config.getBucketName()).prefix(prefix).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                // 过滤掉目录本身
                if (!objectName.endsWith("/")) {
                    // 返回相对路径（去掉目录前缀）
                    fileNames.add(objectName.substring(prefix.length()));
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_002, "列出文件失败: " + e.getMessage());
        }
        return fileNames;
    }

    /** 列出目录下所有子目录名（仅直接子目录，不含文件） */
    public List<String> listDirectories(FileServerConfig config, String directoryPath) {
        MinioClient client = createClient(config);
        List<String> dirNames = new ArrayList<>();
        try {
            String prefix = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(config.getBucketName())
                            .prefix(prefix)
                            .delimiter("/")
                            .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                // 子目录条目以 delimiter 结尾，去掉前缀和尾部 delimiter
                if (objectName.endsWith("/") && objectName.startsWith(prefix) && !objectName.equals(prefix)) {
                    dirNames.add(objectName.substring(prefix.length(), objectName.length() - 1));
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_002, "列出子目录失败: " + e.getMessage());
        }
        return dirNames;
    }

    /** 从 MinIO 下载文件到本地临时目录 */
    public Path downloadFile(FileServerConfig config, String objectName) {
        MinioClient client = createClient(config);
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("sorting-");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_002, "创建临时目录失败: " + e.getMessage());
        }
        Path targetPath = tempDir.resolve(objectName.replace('/', '_'));
        try (InputStream stream = client.getObject(
                GetObjectArgs.builder().bucket(config.getBucketName()).object(objectName).build())) {
            Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // 清理临时文件/目录，防止泄漏
            try { Files.deleteIfExists(targetPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
            throw new BusinessException(ErrorCode.FILE_002, "下载文件失败: " + e.getMessage());
        }
        return targetPath;
    }

    /** 在 MinIO bucket 内移动文件 */
    public void moveFile(FileServerConfig config, String sourceObject, String targetObject) {
        MinioClient client = createClient(config);
        try {
            // 复制到目标位置
            client.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(config.getBucketName())
                            .object(targetObject)
                            .source(
                                    CopySource.builder()
                                            .bucket(config.getBucketName())
                                            .object(sourceObject)
                                            .build())
                            .build());
            // 删除源文件
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(config.getBucketName())
                            .object(sourceObject)
                            .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_002, "移动文件失败: " + e.getMessage());
        }
    }

    /** 删除 MinIO 上的文件（含目录递归） */
    public void removeObject(FileServerConfig config, String objectName) {
        MinioClient client = createClient(config);
        try {
            // 尝试作为目录删除（递归）
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder().bucket(config.getBucketName()).prefix(objectName).recursive(true).build());
            boolean hasItems = false;
            for (Result<Item> result : results) {
                hasItems = true;
                Item item = result.get();
                client.removeObject(
                        RemoveObjectArgs.builder().bucket(config.getBucketName()).object(item.objectName()).build());
            }
            if (!hasItems) {
                // 单个文件
                client.removeObject(
                        RemoveObjectArgs.builder().bucket(config.getBucketName()).object(objectName).build());
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_002, "删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 解析归档路径，生成按日期的目标路径。
     * 如 /backup/20260715/filename.zip
     */
    public String buildArchivePath(String baseDir, String pattern, String fileName) {
        String dateStr = java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern(pattern));
        return baseDir + "/" + dateStr + "/" + fileName;
    }
}
