package com.example.sorting.pipeline;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.CdrRecord;
import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.repository.CdrRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StepHandlerTest {

    @Mock private MasterDataCache masterDataCache;
    @Mock private CdrRecordMapper cdrRecordMapper;
    @InjectMocks private ParseStepHandler parseHandler;
    @InjectMocks private PersistStepHandler persistHandler;
    @InjectMocks private ValidateStepHandler validateHandler;

    private StepContext context;

    @BeforeEach
    void setUp() {
        SortingTask task = new SortingTask();
        task.setId(UUID.randomUUID().toString());
        context = new StepContext(task);

        Operator op = new Operator();
        op.setId("op-1");
        op.setCode("ColoCloud");
        ServiceAz az = new ServiceAz();
        az.setId("az-1");
        az.setCode("az1");
        UsageUnit unit = new UsageUnit();
        unit.setId("unit-1");
        unit.setCode("MB");

        when(masterDataCache.getOperatorByCode("ColoCloud")).thenReturn(op);
        when(masterDataCache.getAzByCode("az1")).thenReturn(az);
        when(masterDataCache.getUnitByCode("MB")).thenReturn(unit);
    }

    @Test
    void validate_shouldAcceptValidFileName() {
        context.setAttribute(StepContext.KEY_FILE_LIST,
            List.of("ColoCloud_az1_202607120800_202607120930_asia.zip"));
        StepResult result = validateHandler.execute(context);
        assertTrue(result.isSuccess());
        List<String> validFiles = context.getAttribute(StepContext.KEY_VALID_FILES);
        assertEquals(1, validFiles.size());
    }

    @Test
    void validate_shouldRejectInvalidFileName() {
        context.setAttribute(StepContext.KEY_FILE_LIST,
            List.of("invalid-file-name.txt", "Bad_File_.zip"));
        StepResult result = validateHandler.execute(context);
        assertTrue(result.isSuccess());
        List<String> validFiles = context.getAttribute(StepContext.KEY_VALID_FILES);
        assertEquals(0, validFiles.size());
    }

    @Test
    void validate_shouldFailOnEmptyList() {
        context.setAttribute(StepContext.KEY_FILE_LIST, List.of());
        StepResult result = validateHandler.execute(context);
        assertTrue(result.isFailed());
    }

    @Test
    void parse_shouldGenerateRecords() {
        context.setAttribute(StepContext.KEY_EXTRACTED_FILES,
            List.of("ColoCloud_az1_202607120800_202607120930.csv"));
        StepResult result = parseHandler.execute(context);
        assertTrue(result.isSuccess());
        List<CdrRecord> records = context.getAttribute(StepContext.KEY_CDR_RECORDS);
        assertNotNull(records);
        assertFalse(records.isEmpty());
    }

    @Test
    void persist_shouldSucceedWithRecords() {
        List<CdrRecord> records = List.of(new CdrRecord());
        context.setAttribute(StepContext.KEY_CDR_RECORDS, records);
        StepResult result = persistHandler.execute(context);
        assertTrue(result.isSuccess());
    }

    @Test
    void persist_shouldHandleEmptyRecords() {
        context.setAttribute(StepContext.KEY_CDR_RECORDS, List.of());
        StepResult result = persistHandler.execute(context);
        assertTrue(result.isSuccess());
    }
}
