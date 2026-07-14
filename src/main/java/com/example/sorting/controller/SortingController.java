package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.SortingDetailReq;
import com.example.sorting.dto.SortingListReq;
import com.example.sorting.dto.SortingRetryReq;
import com.example.sorting.entity.SortingStepLog;
import com.example.sorting.entity.SortingTask;
import com.example.sorting.service.SortingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 话单分拣任务控制层
 */
@RestController
@RequestMapping("/sorting")
public class SortingController {

    @Autowired
    private SortingService sortingService;

    @PostMapping("/trigger")
    public ApiResponse<Integer> trigger() {
        int count = sortingService.trigger();
        return ApiResponse.success(count);
    }

    @PostMapping("/list")
    public ApiResponse<List<SortingTask>> list(@RequestBody(required = false) SortingListReq req) {
        String status = (req != null) ? req.getStatus() : null;
        return ApiResponse.success(sortingService.list(status));
    }

    @PostMapping("/retry")
    public ApiResponse<Void> retry(@Valid @RequestBody SortingRetryReq req) {
        sortingService.retry(req.getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(@Valid @RequestBody SortingDetailReq req) {
        SortingTask task = sortingService.detail(req.getId());
        List<SortingStepLog> logs = sortingService.stepLogs(req.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("stepLogs", logs);
        return ApiResponse.success(result);
    }
}
