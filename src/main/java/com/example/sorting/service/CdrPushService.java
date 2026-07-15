package com.example.sorting.service;

/**
 * 话单推送服务接口
 */
public interface CdrPushService {

    /** 推送所有 PENDING 状态的待发送记录 */
    void pushPendingRecords();

    /** 重试所有 FAILED 状态的推送记录 */
    void retryFailedRecords();

    /** 批量推送指定数量（返回成功条数） */
    int batchPush(int batchSize);

    /** 推送类型标识 */
    String getType();
}
