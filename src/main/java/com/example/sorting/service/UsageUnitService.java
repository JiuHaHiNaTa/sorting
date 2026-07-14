package com.example.sorting.service;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.UsageUnitMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UsageUnitService {

    @Autowired
    private UsageUnitMapper usageUnitMapper;
    @Autowired
    private MasterDataCache masterDataCache;

    @Transactional
    public UsageUnit add(String code, String name) {
        if (usageUnitMapper.countByCode(code) > 0) {
            throw new BusinessException(ErrorCode.UNIT_002);
        }
        UsageUnit unit = new UsageUnit();
        unit.setId(UUID.randomUUID().toString());
        unit.setCode(code);
        unit.setName(name);
        unit.setCreatedAt(LocalDateTime.now());
        unit.setUpdatedAt(LocalDateTime.now());
        usageUnitMapper.insert(unit);
        masterDataCache.refreshUnits();
        return unit;
    }

    @Transactional
    public UsageUnit modify(String id, String code, String name) {
        UsageUnit existing = usageUnitMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.UNIT_001);
        }
        if (code != null && !code.isEmpty()) {
            UsageUnit byCode = usageUnitMapper.selectByCode(code);
            if (byCode != null && !byCode.getId().equals(id)) {
                throw new BusinessException(ErrorCode.UNIT_002);
            }
            existing.setCode(code);
        }
        if (name != null && !name.isEmpty()) {
            existing.setName(name);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        usageUnitMapper.updateById(existing);
        masterDataCache.refreshUnits();
        return usageUnitMapper.selectById(id);
    }

    @Transactional
    public void delete(String id) {
        UsageUnit existing = usageUnitMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.UNIT_001);
        }
        usageUnitMapper.deleteById(id);
        masterDataCache.refreshUnits();
    }

    public List<UsageUnit> list() {
        return usageUnitMapper.selectAll();
    }
}
