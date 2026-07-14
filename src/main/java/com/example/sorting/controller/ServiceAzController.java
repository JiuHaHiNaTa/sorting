package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.IdReq;
import com.example.sorting.dto.ServiceAzAddReq;
import com.example.sorting.dto.ServiceAzModifyReq;
import com.example.sorting.entity.ServiceAz;
import com.example.sorting.service.ServiceAzService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/az")
public class ServiceAzController {

    private final ServiceAzService serviceAzService;

    public ServiceAzController(ServiceAzService serviceAzService) {
        this.serviceAzService = serviceAzService;
    }

    @PostMapping("/add")
    public ApiResponse<ServiceAz> add(@Valid @RequestBody ServiceAzAddReq req) {
        return ApiResponse.success(serviceAzService.add(req.getCode(), req.getName()));
    }

    @PostMapping("/modify")
    public ApiResponse<ServiceAz> modify(@Valid @RequestBody ServiceAzModifyReq req) {
        return ApiResponse.success(serviceAzService.modify(req.getId(), req.getCode(), req.getName()));
    }

    @PostMapping("/list")
    public ApiResponse<List<ServiceAz>> list() {
        return ApiResponse.success(serviceAzService.list());
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdReq req) {
        serviceAzService.delete(req.getId());
        return ApiResponse.success(null);
    }
}
