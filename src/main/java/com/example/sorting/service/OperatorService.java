package com.example.sorting.service;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.Operator;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.exception.ErrorCode;
import com.example.sorting.repository.OperatorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OperatorService {

    @Autowired
    private OperatorMapper operatorMapper;
    @Autowired
    private MasterDataCache masterDataCache;

    @Transactional
    public Operator add(String code, String name) {
        if (operatorMapper.countByCode(code) > 0) {
            throw new BusinessException(ErrorCode.OP_002);
        }
        Operator op = new Operator();
        op.setId(UUID.randomUUID().toString());
        op.setCode(code);
        op.setName(name);
        op.setCreatedAt(LocalDateTime.now());
        op.setUpdatedAt(LocalDateTime.now());
        operatorMapper.insert(op);
        masterDataCache.refreshOperators();
        return op;
    }

    @Transactional
    public Operator modify(String id, String code, String name) {
        Operator existing = operatorMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.OP_001);
        }
        if (code != null && !code.isEmpty()) {
            Operator byCode = operatorMapper.selectByCode(code);
            if (byCode != null && !byCode.getId().equals(id)) {
                throw new BusinessException(ErrorCode.OP_002);
            }
            existing.setCode(code);
        }
        if (name != null && !name.isEmpty()) {
            existing.setName(name);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        operatorMapper.updateById(existing);
        masterDataCache.refreshOperators();
        return operatorMapper.selectById(id);
    }

    @Transactional
    public void delete(String id) {
        Operator existing = operatorMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.OP_001);
        }
        operatorMapper.deleteById(id);
        masterDataCache.refreshOperators();
    }

    public List<Operator> list() {
        return operatorMapper.selectAll();
    }
}
