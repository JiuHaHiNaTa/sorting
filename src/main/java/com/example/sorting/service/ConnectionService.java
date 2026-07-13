package com.example.sorting.service;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.ConfigMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionService {

    private final ConfigMapper configMapper;

    public ConnectionService(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * 检查指定配置的 MinIO 服务器连通性。
     *
     * @param configId 配置 ID
     * @return ApiResponse，连通成功返回 SUCCESS，失败返回对应错误码
     */
    @Transactional
    public ApiResponse<?> checkConnection(String configId) {
        FileServerConfig config = configMapper.findById(configId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));

        try {
            MinioClient client = createMinioClient(config);
            boolean bucketExists = client.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(config.getBucketName())
                            .build());

            if (!bucketExists) {
                return ApiResponse.error(ErrorCode.CNN_002.getCode(),
                        ErrorCode.CNN_002.getMessage() + ": " + config.getBucketName());
            }

            configMapper.updateConnectivityStatus(configId, true);
            return ApiResponse.success();

        } catch (MinioException e) {
            return ApiResponse.error(ErrorCode.CNN_001.getCode(),
                    ErrorCode.CNN_001.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(ErrorCode.CNN_003.getCode(),
                    ErrorCode.CNN_003.getMessage() + ": " + e.getMessage());
        }
    }

    /**
     * 根据配置创建 MinIO 客户端实例。
     * 包级可见，方便测试中使用 spy 进行模拟。
     */
    MinioClient createMinioClient(FileServerConfig config) {
        return MinioClient.builder()
                .endpoint(config.getServerAddress(), Integer.parseInt(config.getServerPort()), false)
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
    }
}
