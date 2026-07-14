package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.IdReq;
import com.example.sorting.dto.UsageUnitAddReq;
import com.example.sorting.dto.UsageUnitModifyReq;
import com.example.sorting.entity.UsageUnit;
import com.example.sorting.service.UsageUnitService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/usage-unit")
public class UsageUnitController {

    private final UsageUnitService usageUnitService;

    public UsageUnitController(UsageUnitService usageUnitService) {
        this.usageUnitService = usageUnitService;
    }

    @PostMapping("/add")
    public ApiResponse<UsageUnit> add(@Valid @RequestBody UsageUnitAddReq req) {
        return ApiResponse.success(usageUnitService.add(req.getCode(), req.getName()));
    }

    @PostMapping("/modify")
    public ApiResponse<UsageUnit> modify(@Valid @RequestBody UsageUnitModifyReq req) {
        return ApiResponse.success(usageUnitService.modify(req.getId(), req.getCode(), req.getName()));
    }

    @PostMapping("/list")
    public ApiResponse<List<UsageUnit>> list() {
        return ApiResponse.success(usageUnitService.list());
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdReq req) {
        usageUnitService.delete(req.getId());
        return ApiResponse.success(null);
    }
}
