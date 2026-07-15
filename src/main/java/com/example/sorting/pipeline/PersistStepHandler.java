package com.example.sorting.pipeline;

import com.example.sorting.entity.CdrRecord;
import com.example.sorting.repository.CdrRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(5)
public class PersistStepHandler implements StepHandler {

    @Autowired
    private CdrRecordMapper cdrRecordMapper;

    @Override
    public String getStepName() {
        return "persist";
    }

    @Override
    public StepResult execute(StepContext context) {
        List<CdrRecord> records = context.getAttribute(StepContext.KEY_CDR_RECORDS);
        if (records == null || records.isEmpty()) {
            return StepResult.ok("没有需要持久化的记录");
        }

        cdrRecordMapper.batchInsert(records);
        return StepResult.ok("成功持久化 " + records.size() + " 条话单记录");
    }
}
