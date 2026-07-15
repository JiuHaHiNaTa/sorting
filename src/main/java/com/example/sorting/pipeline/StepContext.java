package com.example.sorting.pipeline;

import com.example.sorting.entity.SortingTask;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 步骤上下文，在 Pipeline 内部传递数据。
 * 每个步骤可以向 context 中放数据，后续步骤读取。
 */
public class StepContext {
    private final SortingTask task;
    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

    public StepContext(SortingTask task) {
        this.task = task;
    }

    public SortingTask getTask() { return task; }
    public String getTaskId() { return task.getId(); }

    public void setAttribute(String key, Object value) { data.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) { return (T) data.get(key); }

    // 预定义的常用 key
    public static final String KEY_FILE_LIST = "fileList";
    public static final String KEY_VALID_FILES = "validFiles";
    public static final String KEY_EXTRACTED_FILES = "extractedFiles";
    public static final String KEY_CDR_RECORDS = "cdrRecords";
    public static final String KEY_ERROR_FILES = "errorFiles";
}
