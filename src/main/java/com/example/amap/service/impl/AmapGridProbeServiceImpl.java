package com.example.amap.service.impl;
import com.example.amap.client.AmapClient;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.amap.mapper.AmapGridProbeMapper;
import com.example.amap.config.AmapProperties;
import com.example.amap.entity.AmapGridProbe;
import com.example.amap.enums.ProbeStatus;
import com.example.amap.exception.BusinessException;
import com.example.amap.model.*;
import com.example.amap.service.*;
import com.example.amap.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmapGridProbeServiceImpl extends ServiceImpl<AmapGridProbeMapper, AmapGridProbe> implements AmapGridProbeService {
    private final AmapClient client;
    private final AmapProperties properties;
    private final ProbeRecordService recorder;
    @Override
    public ProbeResult probeRecommendedGridSize(String batchNo, String region, String types, Geometry city) {
        var p = properties.getPoiGrid();
        int size = p.getInitialGridSizeMeter(), level = 0;
        PreparedGeometry prepared = PreparedGeometryFactory.prepare(city);
        List<GridCell> cells = filter(GridUtils.cover(city.getEnvelopeInternal(), size), prepared);
        while (true) {
            if (cells.isEmpty()) throw new BusinessException("探测没有有效城市网格");
            log.info("[GridProbe] level={} gridSize={} probeGridCount={}", level, size, cells.size());
            List<CountedGrid> results = new ArrayList<>();
            for (GridCell cell : cells) {
                int currentSize = size, currentLevel = level;
                int count = client.poiCount(region, types, cell,
                    (value, error) -> record(batchNo, region, types, cell, currentSize, currentLevel, value, error));
                results.add(new CountedGrid(cell, count));
            }
            List<CountedGrid> top = selectTop(results, p.getTopDensityGridCount());
            if (top.isEmpty()) throw new BusinessException("探测没有得到有效结果");
            int max = top.get(0).count();
            log.info("[GridProbe] level={} gridSize={} maxCount={} topCount={}", level, size, max, top.size());
            if (isSafe(top, p.getSafeCount()) || size == p.getMinGridSizeMeter()) {
                if (max > p.getSafeCount()) log.warn("[GridProbe] minimum reached region={} types={} gridSize={} maxCount={} safeCount={}",
                    region, types, size, max, p.getSafeCount());
                log.info("[GridProbe] recommendedGridSize={}", size);
                return new ProbeResult(size, max);
            }
            int next = Math.max(size / 2, p.getMinGridSizeMeter());
            List<GridCell> children = new ArrayList<>();
            // 所有 TOP N 的四个子网格统一竞争；绝不按父网格各挑一个。
            for (CountedGrid parent : top) children.addAll(GridUtils.splitFour(parent.grid()));
            if (size / 2 < p.getMinGridSizeMeter()) {
                // 非二分的最小尺寸截断：仍保留四等分探测，并增加真实 min 米尺寸探测。
                // 不能把156米的四叉树子格标为250米然后判定250米安全。
                List<GridCell> quarters = filter(children, prepared);
                for (GridCell child : quarters) {
                    int actualNominal = Math.max(1, size / 2);
                    int probeLevel = level + 1;
                    client.poiCount(region, types, child,
                        (value, error) -> record(batchNo, region, types, child, actualNominal, probeLevel, value, error));
                }
                children = new ArrayList<>();
                for (CountedGrid parent : top) {
                    GridCell g = parent.grid();
                    children.addAll(GridUtils.cover(new Envelope(g.minLng(), g.maxLng(), g.minLat(), g.maxLat()), next));
                }
                children = new ArrayList<>(new LinkedHashSet<>(children));
            }
            cells = filter(children, prepared);
            size = next;
            level++;
        }
    }
    public static List<CountedGrid> selectTop(List<CountedGrid> results, int count) {
        return results.stream().sorted(Comparator.comparingInt(CountedGrid::count).reversed()
            .thenComparingDouble(x -> x.grid().minLat()).thenComparingDouble(x -> x.grid().minLng()))
            .limit(count).toList();
    }
    public static boolean isSafe(List<CountedGrid> top, int safeCount) {
        return !top.isEmpty() && top.stream().allMatch(x -> x.count() <= safeCount);
    }
    private List<GridCell> filter(List<GridCell> cells, PreparedGeometry city) {
        return cells.stream().filter(g -> city.intersects(GeometryUtils.polygon(g))).toList();
    }
    private void record(String batchNo, String region, String types, GridCell g, int size, int level, Integer value, BusinessException error) {
        AmapGridProbe record = new AmapGridProbe();
        record.setBatchNo(batchNo); record.setRegion(region); record.setPoiType(types);
        record.setGridSizeMeter(size); record.setProbeLevel(level); record.setPoiCount(value);
        record.setMinLng(BigDecimal.valueOf(g.minLng())); record.setMinLat(BigDecimal.valueOf(g.minLat()));
        record.setMaxLng(BigDecimal.valueOf(g.maxLng())); record.setMaxLat(BigDecimal.valueOf(g.maxLat()));
        record.setCenterLng(BigDecimal.valueOf(g.center().lng())); record.setCenterLat(BigDecimal.valueOf(g.center().lat()));
        record.setProbeStatus(error == null ? ProbeStatus.SUCCESS.name() : ProbeStatus.FAILED.name());
        record.setErrorMessage(error == null ? null : error.getMessage());
        recorder.saveAttempt(record);
    }
}
