package com.example.sorting.cache;

import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.repository.OperatorMapper;
import com.example.sorting.repository.ServiceAzMapper;
import com.example.sorting.repository.UsageUnitMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MasterDataCache {

    @Autowired
    private OperatorMapper operatorMapper;
    @Autowired
    private ServiceAzMapper serviceAzMapper;
    @Autowired
    private UsageUnitMapper usageUnitMapper;

    private volatile Map<String, Operator> operatorCache = Collections.emptyMap();
    private volatile Map<String, ServiceAz> azCache = Collections.emptyMap();
    private volatile Map<String, UsageUnit> unitCache = Collections.emptyMap();

    @PostConstruct
    public void init() {
        refreshAll();
    }

    public void refreshOperators() {
        List<Operator> list = operatorMapper.selectAll();
        Map<String, Operator> map = new ConcurrentHashMap<>();
        for (Operator op : list) {
            map.put(op.getCode(), op);
        }
        this.operatorCache = map;
    }

    public void refreshAzs() {
        List<ServiceAz> list = serviceAzMapper.selectAll();
        Map<String, ServiceAz> map = new ConcurrentHashMap<>();
        for (ServiceAz az : list) {
            map.put(az.getCode(), az);
        }
        this.azCache = map;
    }

    public void refreshUnits() {
        List<UsageUnit> list = usageUnitMapper.selectAll();
        Map<String, UsageUnit> map = new ConcurrentHashMap<>();
        for (UsageUnit unit : list) {
            map.put(unit.getCode(), unit);
        }
        this.unitCache = map;
    }

    public void refreshAll() {
        refreshOperators();
        refreshAzs();
        refreshUnits();
    }

    @Scheduled(fixedRate = 300_000)
    public void scheduledRefresh() {
        refreshAll();
    }

    public Operator getOperatorByCode(String code) {
        return operatorCache.get(code);
    }

    public ServiceAz getAzByCode(String code) {
        return azCache.get(code);
    }

    public UsageUnit getUnitByCode(String code) {
        return unitCache.get(code);
    }

    public boolean operatorExists(String code) {
        return operatorCache.containsKey(code);
    }

    public boolean azExists(String code) {
        return azCache.containsKey(code);
    }

    public boolean unitExists(String code) {
        return unitCache.containsKey(code);
    }
}
