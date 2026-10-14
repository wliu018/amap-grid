package com.example.amap.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.amap.config.AmapProperties;
import com.example.amap.entity.AmapGrid;
import com.example.amap.enums.GridStatus;
import com.example.amap.exception.BusinessException;
import com.example.amap.mapper.AmapGridMapper;
import com.example.amap.model.GridCell;
import com.example.amap.service.AmapGridService;
import com.example.amap.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapGridServiceImpl extends ServiceImpl<AmapGridMapper, AmapGrid> implements AmapGridService {
    private final AmapProperties properties;
    @Override
    public List<AmapGrid> generateCityGrids(String region, String types, Geometry city, int meter) {
        List<GridCell> candidates = GridUtils.cover(city.getEnvelopeInternal(), meter);
        log.info("[GridGenerate] candidateGridCount={}", candidates.size());
        PreparedGeometry prepared = PreparedGeometryFactory.prepare(city);
        List<AmapGrid> grids = new ArrayList<>();
        int inside = 0;
        for (GridCell cell : candidates) {
            var polygon = GeometryUtils.polygon(cell);
            if (!prepared.intersects(polygon)) continue;
            int relation = prepared.contains(polygon) ? 2 : 1;
            if (relation == 2) inside++;
            AmapGrid grid = new AmapGrid();
            grid.setRegion(region); grid.setPoiType(types);
            grid.setGridNo(GridUtils.stableGridNo(region, types, cell.rowIndex(), cell.colIndex()));
            grid.setRowIndex(cell.rowIndex()); grid.setColIndex(cell.colIndex());
            grid.setMinLng(BigDecimal.valueOf(cell.minLng())); grid.setMinLat(BigDecimal.valueOf(cell.minLat()));
            grid.setMaxLng(BigDecimal.valueOf(cell.maxLng())); grid.setMaxLat(BigDecimal.valueOf(cell.maxLat()));
            grid.setCenterLng(BigDecimal.valueOf(cell.center().lng())); grid.setCenterLat(BigDecimal.valueOf(cell.center().lat()));
            grid.setGridSizeMeter(meter); grid.setRelationType(relation); grid.setStatus(GridStatus.WAITING.name());
            grids.add(grid);
        }
        if (grids.isEmpty()) throw new BusinessException("没有有效城市网格");
        log.info("[GridGenerate] validGridCount={} insideGridCount={} boundaryGridCount={}", grids.size(), inside, grids.size() - inside);
        return grids;
    }
    @Override public void randomizeOrder(List<AmapGrid> grids) {
        Collections.shuffle(grids);
        for (int i = 0; i < grids.size(); i++) grids.get(i).setRandomOrder((long)i + 1);
        log.info("[GridGenerate] random order generated");
    }
    @Override public void saveGridBatch(List<AmapGrid> grids) {
        int size = properties.getPoiGrid().getDatabaseBatchSize();
        for (int from = 0; from < grids.size(); from += size) {
            List<AmapGrid> chunk = grids.subList(from, Math.min(from + size, grids.size()));
            if (baseMapper.insertBatch(chunk) != chunk.size()) throw new IllegalStateException("网格批量保存数量不一致");
        }
        log.info("[GridGenerate] batch save completed batchNo={}", grids.get(0).getBatchNo());
    }
}
