package com.example.amap.service;
import com.example.amap.entity.*;
import com.example.amap.enums.GridBatchStatus;
import com.example.amap.model.ProbeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service
@RequiredArgsConstructor
public class BatchPersistenceService {
    private final AmapGridService grids;
    private final AmapGridConfigService configs;
    private final AmapGridBatchService batches;
    /** 单独 Bean，确保事务代理生效；仅含最终数据库写入，没有任何 HTTP 请求。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveNewBatch(AmapGridBatch batch, ProbeResult result, List<AmapGrid> cells) {
        for (AmapGrid grid : cells) grid.setBatchNo(batch.getBatchNo());
        grids.saveGridBatch(cells);
        int inside = (int)cells.stream().filter(g -> g.getRelationType() == 2).count();
        batch.setRecommendedGridSize(result.recommendedGridSize()); batch.setMaxProbeCount(result.maxProbeCount());
        batch.setGridCount(cells.size()); batch.setInsideGridCount(inside); batch.setBoundaryGridCount(cells.size() - inside);
        batch.setStatus(GridBatchStatus.SUCCESS.name()); batch.setFinishTime(LocalDateTime.now());
        if (!batches.updateById(batch)) throw new IllegalStateException("batch SUCCESS 更新失败");
        AmapGridConfig config = new AmapGridConfig();
        config.setRegion(batch.getRegion()); config.setPoiType(batch.getPoiType());
        config.setRecommendedGridSize(result.recommendedGridSize()); config.setSafeCount(batch.getSafeCount());
        config.setMaxProbeCount(result.maxProbeCount()); config.setCurrentBatchNo(batch.getBatchNo());
        configs.upsert(config);
    }
}
