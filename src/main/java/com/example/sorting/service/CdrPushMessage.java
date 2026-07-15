package com.example.sorting.service;

import java.math.BigDecimal;

/**
 * Kafka 推送消息体
 */
public class CdrPushMessage {
    private String messageId;
    private String cdrRecordId;
    private String pushedAt;
    private Payload payload;

    public CdrPushMessage() {}

    public CdrPushMessage(String messageId, String cdrRecordId, String pushedAt, Payload payload) {
        this.messageId = messageId;
        this.cdrRecordId = cdrRecordId;
        this.pushedAt = pushedAt;
        this.payload = payload;
    }

    // Getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getCdrRecordId() { return cdrRecordId; }
    public void setCdrRecordId(String cdrRecordId) { this.cdrRecordId = cdrRecordId; }
    public String getPushedAt() { return pushedAt; }
    public void setPushedAt(String pushedAt) { this.pushedAt = pushedAt; }
    public Payload getPayload() { return payload; }
    public void setPayload(Payload payload) { this.payload = payload; }

    public static class Payload {
        private String operatorCode;
        private String azCode;
        private String resourceId;
        private BigDecimal usageAmount;
        private String usageUnitCode;
        private String startTime;
        private String endTime;

        public Payload() {}

        // Getters and setters
        public String getOperatorCode() { return operatorCode; }
        public void setOperatorCode(String operatorCode) { this.operatorCode = operatorCode; }
        public String getAzCode() { return azCode; }
        public void setAzCode(String azCode) { this.azCode = azCode; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public BigDecimal getUsageAmount() { return usageAmount; }
        public void setUsageAmount(BigDecimal usageAmount) { this.usageAmount = usageAmount; }
        public String getUsageUnitCode() { return usageUnitCode; }
        public void setUsageUnitCode(String usageUnitCode) { this.usageUnitCode = usageUnitCode; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
    }
}
