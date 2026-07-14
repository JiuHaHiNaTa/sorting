package com.example.sorting.service;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.ServiceAzMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceAzService {

    @Autowired
    private ServiceAzMapper serviceAzMapper;
    @Autowired
    private MasterDataCache masterDataCache;

    @Transactional
    public ServiceAz add(String code, String name) {
        if (serviceAzMapper.countByCode(code) > 0) {
            throw new BusinessException(ErrorCode.AZ_002);
        }
        ServiceAz az = new ServiceAz();
        az.setId(UUID.randomUUID().toString());
        az.setCode(code);
        az.setName(name);
        az.setCreatedAt(LocalDateTime.now());
        az.setUpdatedAt(LocalDateTime.now());
        serviceAzMapper.insert(az);
        masterDataCache.refreshAzs();
        return az;
    }

    @Transactional
    public ServiceAz modify(String id, String code, String name) {
        ServiceAz existing = serviceAzMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.AZ_001);
        }
        if (code != null && !code.isEmpty()) {
            ServiceAz byCode = serviceAzMapper.selectByCode(code);
            if (byCode != null && !byCode.getId().equals(id)) {
                throw new BusinessException(ErrorCode.AZ_002);
            }
            existing.setCode(code);
        }
        if (name != null && !name.isEmpty()) {
            existing.setName(name);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        serviceAzMapper.updateById(existing);
        masterDataCache.refreshAzs();
        return serviceAzMapper.selectById(id);
    }

    @Transactional
    public void delete(String id) {
        ServiceAz existing = serviceAzMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.AZ_001);
        }
        serviceAzMapper.deleteById(id);
        masterDataCache.refreshAzs();
    }

    public List<ServiceAz> list() {
        return serviceAzMapper.selectAll();
    }
}
