package com.example.sorting.controller;

import com.example.sorting.dto.ApiResponse;
import com.example.sorting.dto.ConnectionCheckReq;
import com.example.sorting.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/connection/check")
    public ApiResponse<?> checkConnection(@Valid @RequestBody ConnectionCheckReq req) {
        return connectionService.checkConnection(req.getId());
    }
}
