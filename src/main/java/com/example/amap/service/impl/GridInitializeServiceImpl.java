package com.example.amap.service.impl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.amap.config.AmapProperties;
import com.example.amap.dto.request.GridInitRequest;
import com.example.amap.dto.response.GridInitResponse;
import com.example.amap.entity.*;
import com.example.amap.enums.GridBatchStatus;
import com.example.amap.exception.BusinessException;
import com.example.amap.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GridInitializeServiceImpl implements GridInitializeService {
    private final AmapProperties properties;
    private final AmapDistrictService districtService;
    private final AmapGridProbeService probeService;
    private final AmapGridService gridService;
    private final AmapGridConfigService configService;
    private final AmapGridBatchService batchService;
    private final BatchStateService batchState;
    private final BatchPersistenceService persistence;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Override
    public GridInitResponse initialize(GridInitRequest request) {
        String region = request.getRegion() != null ? request.getRegion() : properties.getPoiGrid().getRegion();
        String types = request.getTypes() != null ? request.getTypes() : properties.getPoiGrid().getTypes();
        if (region == null || !region.matches("[0-9]{6}")) throw new BusinessException(HttpStatus.BAD_REQUEST, "region 缺失或不是六位 adcode");
        if (types == null || types.length() > 100 || !types.matches("[0-9]{6}(\\|[0-9]{6})*"))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "types 缺失或格式错误");
        // 同进程相同城市/类型串行初始化，force=false 排队后重新检查当前成功批次。
        synchronized (locks.computeIfAbsent(region + ":" + types, ignored -> new Object())) {
            return initializeLocked(region, types, Boolean.TRUE.equals(request.getForce()));
        }
    }
    private GridInitResponse initializeLocked(String region, String types, boolean force) {
        if (!force) {
            AmapGridConfig config = configService.getOne(Wrappers.<AmapGridConfig>lambdaQuery()
                .eq(AmapGridConfig::getRegion, region).eq(AmapGridConfig::getPoiType, types));
            if (config != null && config.getCurrentBatchNo() != null) {
                AmapGridBatch existing = batchService.getOne(Wrappers.<AmapGridBatch>lambdaQuery()
                    .eq(AmapGridBatch::getBatchNo, config.getCurrentBatchNo())
                    .eq(AmapGridBatch::getRegion, region).eq(AmapGridBatch::getPoiType, types)
                    .eq(AmapGridBatch::getStatus, GridBatchStatus.SUCCESS.name()));
                if (existing != null) {
                    log.info("[GridInit] reuse region={} types={} batchNo={}", region, types, existing.getBatchNo());
                    return response(existing);
                }
            }
        }
        log.info("[GridInit] start region={} types={}", region, types);
        // 先用短事务登记 CREATING，方便关联所有探测记录及保存行政区/探测失败状态。
        AmapGridBatch batch = batchState.create(region, types, properties.getPoiGrid().getSafeCount());
        try {
            var cityGeometry = districtService.getCityGeometry(region);
            var result = probeService.probeRecommendedGridSize(batch.getBatchNo(), region, types, cityGeometry);
            var grids = gridService.generateCityGrids(region, types, cityGeometry, result.recommendedGridSize());
            gridService.randomizeOrder(grids);
            persistence.saveNewBatch(batch, result, grids);
            log.info("[GridInit] success region={} types={} batchNo={} gridCount={}", region, types, batch.getBatchNo(), grids.size());
            return response(batch);
        } catch (RuntimeException e) {
            String message = e instanceof BusinessException ? e.getMessage() : "初始化或数据库写入失败";
            try { batchState.fail(batch.getBatchNo(), message); }
            catch (RuntimeException stateError) {
                log.error("[GridInit] FAILED status write failed batchNo={} exceptionType={}", batch.getBatchNo(), stateError.getClass().getSimpleName());
            }
            log.error("[GridInit] failed region={} types={} batchNo={} message={}", region, types, batch.getBatchNo(), message);
            if (e instanceof BusinessException business) throw business;
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, message + "，batchNo=" + batch.getBatchNo());
        }
    }
    private GridInitResponse response(AmapGridBatch b) {
        return new GridInitResponse(b.getRegion(), b.getPoiType(), b.getRecommendedGridSize(),
            b.getGridCount(), b.getInsideGridCount(), b.getBoundaryGridCount(), b.getMaxProbeCount(), b.getBatchNo());
    }
}
