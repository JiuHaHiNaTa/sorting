package com.example.sorting.service;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.exception.BusinessException;
import com.example.sorting.repository.ServiceAzMapper;
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
class ServiceAzServiceTest {

    @Mock
    private ServiceAzMapper serviceAzMapper;
    @Mock
    private MasterDataCache masterDataCache;
    @InjectMocks
    private ServiceAzService serviceAzService;

    @Test
    void add_shouldThrowWhenCodeExists() {
        when(serviceAzMapper.countByCode("dup")).thenReturn(1);
        assertThrows(BusinessException.class,
                () -> serviceAzService.add("dup", "重复"));
        verify(serviceAzMapper, never()).insert(any());
    }

    @Test
    void add_shouldSucceed() {
        when(serviceAzMapper.countByCode("AZ-1")).thenReturn(0);
        ServiceAz result = serviceAzService.add("AZ-1", "可用区一");
        assertNotNull(result.getId());
        assertEquals("AZ-1", result.getCode());
        assertEquals("可用区一", result.getName());
        verify(serviceAzMapper).insert(any(ServiceAz.class));
        verify(masterDataCache).refreshAzs();
    }

    @Test
    void modify_shouldThrowWhenNotFound() {
        when(serviceAzMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> serviceAzService.modify("non-existent", null, "新名称"));
    }

    @Test
    void modify_shouldSucceed() {
        ServiceAz existing = new ServiceAz();
        existing.setId("id-1");
        existing.setCode("AZ-1");
        existing.setName("旧名称");
        when(serviceAzMapper.selectById("id-1")).thenReturn(existing);

        ServiceAz result = serviceAzService.modify("id-1", null, "新名称");

        assertEquals("新名称", result.getName());
        verify(serviceAzMapper).updateById(any(ServiceAz.class));
        verify(masterDataCache).refreshAzs();
    }

    @Test
    void modify_shouldThrowWhenCodeAlreadyUsedByOther() {
        ServiceAz existing = new ServiceAz();
        existing.setId("id-1");
        existing.setCode("AZ-1");
        existing.setName("可用区一");
        when(serviceAzMapper.selectById("id-1")).thenReturn(existing);

        ServiceAz other = new ServiceAz();
        other.setId("id-2");
        other.setCode("NewCode");
        when(serviceAzMapper.selectByCode("NewCode")).thenReturn(other);

        assertThrows(BusinessException.class,
                () -> serviceAzService.modify("id-1", "NewCode", null));
        verify(serviceAzMapper, never()).updateById(any());
    }

    @Test
    void list_shouldReturnAll() {
        when(serviceAzMapper.selectAll()).thenReturn(List.of(new ServiceAz()));
        assertEquals(1, serviceAzService.list().size());
    }

    @Test
    void delete_shouldSucceed() {
        ServiceAz existing = new ServiceAz();
        existing.setId("id-1");
        when(serviceAzMapper.selectById("id-1")).thenReturn(existing);
        when(serviceAzMapper.deleteById("id-1")).thenReturn(1);

        serviceAzService.delete("id-1");
        verify(serviceAzMapper).deleteById("id-1");
        verify(masterDataCache).refreshAzs();
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(serviceAzMapper.selectById("non-existent")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> serviceAzService.delete("non-existent"));
    }
}
