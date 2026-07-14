package com.example.sorting.service;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.repository.UsageUnitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageUnitServiceTest {

    @Mock
    private UsageUnitMapper usageUnitMapper;
    @Mock
    private MasterDataCache masterDataCache;
    @InjectMocks
    private UsageUnitService usageUnitService;

    @Test
    void add_shouldThrowWhenCodeExists() {
        when(usageUnitMapper.countByCode("dup")).thenReturn(1);
        assertThrows(BusinessException.class,
                () -> usageUnitService.add("dup", "重复"));
        verify(usageUnitMapper, never()).insert(any());
    }

    @Test
    void add_shouldSucceed() {
        when(usageUnitMapper.countByCode("MB")).thenReturn(0);
        UsageUnit result = usageUnitService.add("MB", "兆字节");
        assertNotNull(result.getId());
        assertEquals("MB", result.getCode());
        assertEquals("兆字节", result.getName());
        verify(usageUnitMapper).insert(any(UsageUnit.class));
        verify(masterDataCache).refreshUnits();
    }

    @Test
    void modify_shouldThrowWhenNotFound() {
        when(usageUnitMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> usageUnitService.modify("non-existent", null, "新名称"));
    }

    @Test
    void modify_shouldSucceed() {
        UsageUnit existing = new UsageUnit();
        existing.setId("id-1");
        existing.setCode("MB");
        existing.setName("旧名称");
        when(usageUnitMapper.selectById("id-1")).thenReturn(existing);

        UsageUnit result = usageUnitService.modify("id-1", null, "新名称");

        assertEquals("新名称", result.getName());
        verify(usageUnitMapper).updateById(any(UsageUnit.class));
        verify(masterDataCache).refreshUnits();
    }

    @Test
    void modify_shouldThrowWhenCodeAlreadyUsedByOther() {
        UsageUnit existing = new UsageUnit();
        existing.setId("id-1");
        existing.setCode("MB");
        existing.setName("兆字节");
        when(usageUnitMapper.selectById("id-1")).thenReturn(existing);

        UsageUnit other = new UsageUnit();
        other.setId("id-2");
        other.setCode("NewCode");
        when(usageUnitMapper.selectByCode("NewCode")).thenReturn(other);

        assertThrows(BusinessException.class,
                () -> usageUnitService.modify("id-1", "NewCode", null));
        verify(usageUnitMapper, never()).updateById(any());
    }

    @Test
    void list_shouldReturnAll() {
        when(usageUnitMapper.selectAll()).thenReturn(List.of(new UsageUnit()));
        assertEquals(1, usageUnitService.list().size());
    }

    @Test
    void delete_shouldSucceed() {
        UsageUnit existing = new UsageUnit();
        existing.setId("id-1");
        when(usageUnitMapper.selectById("id-1")).thenReturn(existing);
        when(usageUnitMapper.deleteById("id-1")).thenReturn(1);

        usageUnitService.delete("id-1");
        verify(usageUnitMapper).deleteById("id-1");
        verify(masterDataCache).refreshUnits();
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(usageUnitMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> usageUnitService.delete("non-existent"));
    }
}
