package com.example.sorting.service;

import com.example.sorting.dto.ConfigAddReq;
import com.example.sorting.dto.ConfigModifyReq;
import com.example.sorting.dto.ConfigToggleReq;
import com.example.sorting.entity.FileServerConfig;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.ConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConfigService {

    private final ConfigMapper configMapper;

    public ConfigService(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Transactional
    public FileServerConfig addConfig(ConfigAddReq req) {
        FileServerConfig config = new FileServerConfig();
        config.setId(UUID.randomUUID().toString());
        config.setServerAddress(sanitize(req.getServerAddress()));
        config.setServerPort(sanitize(req.getServerPort()));
        config.setBucketName(sanitize(req.getBucketName()));
        config.setAccessKey(req.getAccessKey());
        config.setSecretKey(req.getSecretKey());
        config.setFileDirectory(sanitize(req.getFileDirectory()));
        config.setConnectivityStatus(false);
        config.setEnabled(false);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        configMapper.insert(config);
        return config;
    }

    @Transactional
    public FileServerConfig modifyConfig(ConfigModifyReq req) {
        FileServerConfig existing = configMapper.findById(req.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));

        existing.setServerAddress(sanitize(req.getServerAddress()));
        existing.setServerPort(sanitize(req.getServerPort()));
        existing.setBucketName(sanitize(req.getBucketName()));
        existing.setAccessKey(req.getAccessKey());
        existing.setSecretKey(req.getSecretKey());
        existing.setFileDirectory(sanitize(req.getFileDirectory()));
        existing.setUpdatedAt(LocalDateTime.now());

        configMapper.update(existing);
        return existing;
    }

    @Transactional
    public FileServerConfig toggleConfig(ConfigToggleReq req) {
        FileServerConfig config = configMapper.findById(req.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));

        if (Boolean.TRUE.equals(req.getEnabled()) && !Boolean.TRUE.equals(config.getConnectivityStatus())) {
            throw new BusinessException(ErrorCode.CONFIG_002);
        }

        configMapper.updateEnabled(req.getId(), req.getEnabled());
        config.setEnabled(req.getEnabled());
        return config;
    }

    @Transactional(readOnly = true)
    public FileServerConfig getConfig(String id) {
        return configMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_001));
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input.replaceAll("(?i)<script[^>]*>.*?</script>", "")
                     .replaceAll("(?i)on\\w+\\s*=\\s*\"[^\"]*\"", "")
                     .replaceAll("(?i)on\\w+\\s*=\\s*'[^']*'", "");
    }
}
