package com.example.sorting.service;

import com.example.sorting.repository.CdrPushRecordMapper;
import com.example.sorting.repository.CdrRecordMapper;
import com.example.sorting.repository.OperatorMapper;
import com.example.sorting.repository.ServiceAzMapper;
import com.example.sorting.repository.UsageUnitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CdrPushKafkaImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CdrPushServiceTest {

    @Mock private CdrPushRecordMapper pushRecordMapper;
    @Mock private CdrRecordMapper cdrRecordMapper;
    @Mock private OperatorMapper operatorMapper;
    @Mock private ServiceAzMapper serviceAzMapper;
    @Mock private UsageUnitMapper usageUnitMapper;
    @InjectMocks private CdrPushKafkaImpl pushService;

    @Test
    void getType_shouldReturnKafka() {
        assertEquals("kafka", pushService.getType());
    }

    @Test
    void batchPush_shouldReturnZero_whenNoKafkaTemplate() {
        int result = pushService.batchPush(100);
        assertEquals(0, result);
    }
}
