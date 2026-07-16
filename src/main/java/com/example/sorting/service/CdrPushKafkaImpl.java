package com.example.sorting.service;

import com.example.sorting.entity.CdrPushRecord;
import com.example.sorting.entity.CdrRecord;
import com.example.sorting.entity.Operator;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.repository.CdrPushRecordMapper;
import com.example.sorting.repository.CdrRecordMapper;
import com.example.sorting.repository.OperatorMapper;
import com.example.sorting.repository.ServiceAzMapper;
import com.example.sorting.repository.UsageUnitMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Kafka 推送实现 — 查询 PENDING 状态的 CdrPushRecord，构建消息并推送到 Kafka
 */
@Component
@ConditionalOnProperty(name = "cdr.push.type", havingValue = "kafka", matchIfMissing = true)
public class CdrPushKafkaImpl implements CdrPushService {

    private static final Logger log = LoggerFactory.getLogger(CdrPushKafkaImpl.class);

    @Autowired
    private CdrPushRecordMapper pushRecordMapper;
    @Autowired
    private CdrRecordMapper cdrRecordMapper;
    @Autowired
    private OperatorMapper operatorMapper;
    @Autowired
    private ServiceAzMapper serviceAzMapper;
    @Autowired
    private UsageUnitMapper usageUnitMapper;
    @Autowired(required = false)
    private KafkaTemplate<String, CdrPushMessage> kafkaTemplate;

    @Value("${cdr.push.topic:cdr-record-push}")
    private String topic;

    @Value("${cdr.push.batch-size:100}")
    private int batchSize;

    @Override
    public String getType() { return "kafka"; }

    @Override
    @Scheduled(fixedRateString = "${cdr.push.retry-interval-ms:60000}")
    public void pushPendingRecords() {
        batchPush(batchSize);
    }

    @Override
    public void retryFailedRecords() {
        // 查询所有 FAILED 记录并重置为 PENDING，由定时推送重试
        log.info("重试所有失败推送记录暂未实现，定时任务会自动重试");
    }

    @Override
    public int batchPush(int batchSize) {
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate 未配置，跳过推送");
            return 0;
        }

        List<CdrPushRecord> pendingRecords = pushRecordMapper.selectPendingByLimit(batchSize);
        if (pendingRecords.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (CdrPushRecord pushRecord : pendingRecords) {
            try {
                String cdrRecordId = pushRecord.getCdrRecordId();

                // 构建消息
                CdrPushMessage message = buildPushMessage(cdrRecordId);
                if (message == null) {
                    pushRecordMapper.updateStatus(pushRecord.getId(), "FAILED",
                            LocalDateTime.now(), "CDR 记录不存在");
                    continue;
                }

                // 发送到 Kafka
                kafkaTemplate.send(topic, message.getCdrRecordId(), message).get();

                // 标记推送成功
                pushRecordMapper.updateStatus(pushRecord.getId(), "PUSHED",
                        LocalDateTime.now(), null);
                successCount++;

            } catch (Exception e) {
                log.error("推送失败 [{}]: {}", pushRecord.getId(), e.getMessage());
                pushRecordMapper.updateStatus(pushRecord.getId(), "FAILED",
                        null, e.getMessage());
            }
        }

        log.info("批量推送完成: {}/{} 成功", successCount, pendingRecords.size());
        return successCount;
    }

    /**
     * 根据 CDR 记录 ID 构建推送消息，将 ID 通过 Mapper 翻译为业务 code
     */
    private CdrPushMessage buildPushMessage(String cdrRecordId) {
        CdrRecord record = cdrRecordMapper.selectById(cdrRecordId);
        if (record == null) {
            return null;
        }

        CdrPushMessage msg = new CdrPushMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setCdrRecordId(cdrRecordId);
        msg.setPushedAt(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));

        // 通过各 Mapper 将 ID 翻译为业务 code
        CdrPushMessage.Payload payload = new CdrPushMessage.Payload();

        String operatorCode = lookupOperatorCode(record.getOperatorId());
        payload.setOperatorCode(operatorCode != null ? operatorCode : "unknown");

        String azCode = lookupAzCode(record.getServiceAzId());
        payload.setAzCode(azCode != null ? azCode : "unknown");

        payload.setResourceId(record.getResourceId());
        payload.setUsageAmount(record.getUsageAmount());

        String unitCode = lookupUnitCode(record.getUsageUnitId());
        payload.setUsageUnitCode(unitCode != null ? unitCode : "unknown");

        payload.setStartTime(record.getStartTime() != null ? record.getStartTime().toString() : null);
        payload.setEndTime(record.getEndTime() != null ? record.getEndTime().toString() : null);

        msg.setPayload(payload);
        return msg;
    }

    private String lookupOperatorCode(String operatorId) {
        if (operatorId == null) return null;
        Operator op = operatorMapper.selectById(operatorId);
        return op != null ? op.getCode() : null;
    }

    private String lookupAzCode(String azId) {
        if (azId == null) return null;
        ServiceAz az = serviceAzMapper.selectById(azId);
        return az != null ? az.getCode() : null;
    }

    private String lookupUnitCode(String unitId) {
        if (unitId == null) return null;
        UsageUnit unit = usageUnitMapper.selectById(unitId);
        return unit != null ? unit.getCode() : null;
    }

    public void setKafkaTemplate(KafkaTemplate<String, CdrPushMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
}
