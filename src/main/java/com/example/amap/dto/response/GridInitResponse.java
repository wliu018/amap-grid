package com.example.amap.dto.response;
public record GridInitResponse(String region, String types, int recommendedGridSize,
    int gridCount, int insideGridCount, int boundaryGridCount, Integer maxProbeCount, String batchNo) { }
