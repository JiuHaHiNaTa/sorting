package com.example.sorting.pipeline;

import com.example.sorting.entity.CdrPushRecord;
import com.example.sorting.entity.CdrRecord;
import com.example.sorting.repository.CdrPushRecordMapper;
import com.example.sorting.repository.CdrRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Order(5)
public class PersistStepHandler implements StepHandler {

    @Autowired
    private CdrRecordMapper cdrRecordMapper;
    @Autowired
    private CdrPushRecordMapper pushRecordMapper;

    @Override
    public String getStepName() {
        return "persist";
    }

    @Override
    @Transactional
    public StepResult execute(StepContext context) {
        List<CdrRecord> records = context.getAttribute(StepContext.KEY_CDR_RECORDS);
        if (records == null || records.isEmpty()) {
            return StepResult.ok("没有需要持久化的记录");
        }

        cdrRecordMapper.batchInsert(records);

        // 同时写入推送记录
        List<CdrPushRecord> pushRecords = new ArrayList<>();
        for (CdrRecord record : records) {
            CdrPushRecord pushRecord = new CdrPushRecord();
            pushRecord.setId(UUID.randomUUID().toString());
            pushRecord.setCdrRecordId(record.getId());
            pushRecord.setPushStatus("PENDING");
            pushRecord.setCreatedAt(LocalDateTime.now());
            pushRecords.add(pushRecord);
        }
        pushRecordMapper.batchInsert(pushRecords);

        return StepResult.ok("成功持久化 " + records.size() + " 条话单记录");
    }
}
