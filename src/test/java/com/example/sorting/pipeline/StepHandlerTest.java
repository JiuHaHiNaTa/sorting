package com.example.sorting.pipeline;

import com.example.sorting.cache.MasterDataCache;
import com.example.sorting.entity.CdrRecord;
import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.repository.CdrErrorRecordMapper;
import com.example.sorting.repository.CdrPushRecordMapper;
import com.example.sorting.repository.CdrRecordMapper;
import com.example.sorting.repository.ConfigMapper;
import com.example.sorting.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StepHandlerTest {

    @Mock private MasterDataCache masterDataCache;
    @Mock private CdrRecordMapper cdrRecordMapper;
    @Mock private CdrErrorRecordMapper errorRecordMapper;
    @Mock private CdrPushRecordMapper pushRecordMapper;
    @Mock private FileService fileService;
    @Mock private ConfigMapper configMapper;

    @InjectMocks private ValidateStepHandler validateHandler;
    @InjectMocks private ParseStepHandler parseHandler;
    @InjectMocks private PersistStepHandler persistHandler;
    @InjectMocks private ArchiveStepHandler archiveHandler;

    private StepContext context;

    @BeforeEach
    void setUp() {
        SortingTask task = new SortingTask();
        task.setId(UUID.randomUUID().toString());
        task.setFileServerId("server-1");
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
        when(configMapper.findById("server-1")).thenReturn(Optional.empty());
    }

    @Test
    void validate_shouldAcceptValidFileName() {
        context.setAttribute(StepContext.KEY_FILE_LIST,
            List.of("ColoCloud_az1_202607120800_202607120930_asia.zip"));
        // configMapper returns empty, so the file won't be moved
        StepResult result = validateHandler.execute(context);
        assertTrue(result.isSuccess());
        List<String> validFiles = context.getAttribute(StepContext.KEY_VALID_FILES);
        assertEquals(1, validFiles.size());
    }

    @Test
    void validate_shouldRejectInvalidFileName() {
        context.setAttribute(StepContext.KEY_FILE_LIST,
            List.of("invalid-file-name.txt", "Bad_File_.zip"));
        // configMapper returns empty, file move skipped
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
    void parse_shouldReturnSuccessWithEmptyFiles() {
        context.setAttribute(StepContext.KEY_EXTRACTED_FILES, List.of());
        StepResult result = parseHandler.execute(context);
        assertTrue(result.isSuccess());
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

    @Test
    void archive_shouldHandleEmptyFileList() {
        context.setAttribute(StepContext.KEY_FILE_LIST, List.of());
        StepResult result = archiveHandler.execute(context);
        assertTrue(result.isSuccess());
    }

    @Test
    void archive_shouldHandleNoConfig() {
        context.setAttribute(StepContext.KEY_FILE_LIST,
            List.of("ColoCloud_az1_202607120800_202607120930_asia.zip"));
        StepResult result = archiveHandler.execute(context);
        assertTrue(result.isFailed());
    }
}
