package com.example.amap.controller;
import com.example.amap.dto.request.GridInitRequest;
import com.example.amap.dto.response.*;
import com.example.amap.service.GridInitializeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/amap/grid")
@RequiredArgsConstructor
public class AmapGridController {
    private final GridInitializeService service;
    @PostMapping("/init")
    public ApiResponse<GridInitResponse> initialize(@Valid @RequestBody(required = false) GridInitRequest request) {
        return ApiResponse.success(service.initialize(request == null ? new GridInitRequest() : request));
    }
}
