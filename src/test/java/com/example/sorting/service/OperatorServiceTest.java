package com.example.sorting.service;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.Operator;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.repository.OperatorMapper;
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
class OperatorServiceTest {

    @Mock
    private OperatorMapper operatorMapper;
    @Mock
    private MasterDataCache masterDataCache;
    @InjectMocks
    private OperatorService operatorService;

    @Test
    void add_shouldThrowWhenCodeExists() {
        when(operatorMapper.countByCode("dup")).thenReturn(1);
        assertThrows(BusinessException.class,
                () -> operatorService.add("dup", "重复"));
        verify(operatorMapper, never()).insert(any());
    }

    @Test
    void add_shouldSucceed() {
        when(operatorMapper.countByCode("ColoCloud")).thenReturn(0);
        Operator result = operatorService.add("ColoCloud", "星辰云");
        assertNotNull(result.getId());
        assertEquals("ColoCloud", result.getCode());
        assertEquals("星辰云", result.getName());
        verify(operatorMapper).insert(any(Operator.class));
        verify(masterDataCache).refreshOperators();
    }

    @Test
    void modify_shouldThrowWhenNotFound() {
        when(operatorMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> operatorService.modify("non-existent", null, "新名称"));
    }

    @Test
    void modify_shouldSucceed() {
        Operator existing = new Operator();
        existing.setId("id-1");
        existing.setCode("ColoCloud");
        existing.setName("旧名称");
        when(operatorMapper.selectById("id-1")).thenReturn(existing);

        Operator result = operatorService.modify("id-1", null, "新名称");

        assertEquals("新名称", result.getName());
        verify(operatorMapper).updateById(any(Operator.class));
        verify(masterDataCache).refreshOperators();
    }

    @Test
    void modify_shouldThrowWhenCodeAlreadyUsedByOther() {
        Operator existing = new Operator();
        existing.setId("id-1");
        existing.setCode("ColoCloud");
        existing.setName("星辰云");
        when(operatorMapper.selectById("id-1")).thenReturn(existing);

        Operator other = new Operator();
        other.setId("id-2");
        other.setCode("NewCode");
        when(operatorMapper.selectByCode("NewCode")).thenReturn(other);

        assertThrows(BusinessException.class,
                () -> operatorService.modify("id-1", "NewCode", null));
        verify(operatorMapper, never()).updateById(any());
    }

    @Test
    void list_shouldReturnAll() {
        when(operatorMapper.selectAll()).thenReturn(List.of(new Operator()));
        assertEquals(1, operatorService.list().size());
    }

    @Test
    void delete_shouldSucceed() {
        Operator existing = new Operator();
        existing.setId("id-1");
        when(operatorMapper.selectById("id-1")).thenReturn(existing);
        when(operatorMapper.deleteById("id-1")).thenReturn(1);

        operatorService.delete("id-1");
        verify(operatorMapper).deleteById("id-1");
        verify(masterDataCache).refreshOperators();
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(operatorMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> operatorService.delete("non-existent"));
    }
}
