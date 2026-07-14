package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.IdReq;
import com.example.sorting.dto.OperatorAddReq;
import com.example.sorting.dto.OperatorModifyReq;
import com.example.sorting.entity.Operator;
import com.example.sorting.service.OperatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/operator")
public class OperatorController {

    private final OperatorService operatorService;

    public OperatorController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    @PostMapping("/add")
    public ApiResponse<Operator> add(@Valid @RequestBody OperatorAddReq req) {
        return ApiResponse.success(operatorService.add(req.getCode(), req.getName()));
    }

    @PostMapping("/modify")
    public ApiResponse<Operator> modify(@Valid @RequestBody OperatorModifyReq req) {
        return ApiResponse.success(operatorService.modify(req.getId(), req.getCode(), req.getName()));
    }

    @PostMapping("/list")
    public ApiResponse<List<Operator>> list() {
        return ApiResponse.success(operatorService.list());
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody IdReq req) {
        operatorService.delete(req.getId());
        return ApiResponse.success(null);
    }
}
