package com.example.sorting.cache;

import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.repository.OperatorMapper;
import com.example.sorting.repository.ServiceAzMapper;
import com.example.sorting.repository.UsageUnitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterDataCacheTest {

    @Mock private OperatorMapper operatorMapper;
    @Mock private ServiceAzMapper serviceAzMapper;
    @Mock private UsageUnitMapper usageUnitMapper;
    @InjectMocks private MasterDataCache cache;

    @Test
    void shouldRefreshAndFindOperatorByCode() {
        Operator op = new Operator();
        op.setCode("ColoCloud");
        op.setName("星辰云");
        when(operatorMapper.selectAll()).thenReturn(List.of(op));

        cache.refreshOperators();

        assertTrue(cache.operatorExists("ColoCloud"));
        assertFalse(cache.operatorExists("NonExistent"));
        assertEquals("星辰云", cache.getOperatorByCode("ColoCloud").getName());
        assertNull(cache.getOperatorByCode("Missing"));
    }

    @Test
    void shouldRefreshAndFindAzByCode() {
        ServiceAz az = new ServiceAz();
        az.setCode("az1");
        az.setName("可用区1");
        when(serviceAzMapper.selectAll()).thenReturn(List.of(az));

        cache.refreshAzs();

        assertTrue(cache.azExists("az1"));
        assertFalse(cache.azExists("az99"));
        assertEquals("az1", cache.getAzByCode("az1").getCode());
    }

    @Test
    void shouldRefreshAndFindUnitByCode() {
        UsageUnit unit = new UsageUnit();
        unit.setCode("MB");
        unit.setName("兆字节");
        when(usageUnitMapper.selectAll()).thenReturn(List.of(unit));

        cache.refreshUnits();

        assertTrue(cache.unitExists("MB"));
        assertFalse(cache.unitExists("GB"));
        assertEquals("MB", cache.getUnitByCode("MB").getCode());
    }

    @Test
    void shouldRefreshAll() {
        Operator op = new Operator();
        op.setCode("op1");
        ServiceAz az = new ServiceAz();
        az.setCode("az1");
        UsageUnit unit = new UsageUnit();
        unit.setCode("unit1");
        when(operatorMapper.selectAll()).thenReturn(List.of(op));
        when(serviceAzMapper.selectAll()).thenReturn(List.of(az));
        when(usageUnitMapper.selectAll()).thenReturn(List.of(unit));

        cache.refreshAll();

        verify(operatorMapper).selectAll();
        verify(serviceAzMapper).selectAll();
        verify(usageUnitMapper).selectAll();
    }
}
