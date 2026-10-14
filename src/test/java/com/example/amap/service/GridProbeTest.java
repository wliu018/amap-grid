package com.example.amap.service;
import com.example.amap.client.AmapClient;
import com.example.amap.config.AmapProperties;
import com.example.amap.entity.AmapGridProbe;
import com.example.amap.exception.BusinessException;
import com.example.amap.model.*;
import com.example.amap.service.impl.AmapGridProbeServiceImpl;
import com.example.amap.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GridProbeTest {
    private AmapProperties properties;
    private AmapClient client;
    private ProbeRecordService recorder;
    private AmapGridProbeServiceImpl service;
    @BeforeEach void setup() {
        properties = new AmapProperties();
        client = mock(AmapClient.class); recorder = mock(ProbeRecordService.class);
        service = new AmapGridProbeServiceImpl(client, properties, recorder);
    }
    @Test void topNCompetesGloballyAndStopChecksAll() {
        GridCell g = new GridCell(0, 0, 1, 1, 1, 1);
        var top = AmapGridProbeServiceImpl.selectTop(List.of(new CountedGrid(g, 80), new CountedGrid(g, 200),
            new CountedGrid(g, 153), new CountedGrid(g, 166), new CountedGrid(g, 187), new CountedGrid(g, 200)), 5);
        assertEquals(List.of(200, 200, 187, 166, 153), top.stream().map(CountedGrid::count).toList());
        assertFalse(AmapGridProbeServiceImpl.isSafe(top, 100));
        assertTrue(AmapGridProbeServiceImpl.isSafe(List.of(new CountedGrid(g, 100), new CountedGrid(g, 92)), 100));
        assertFalse(AmapGridProbeServiceImpl.isSafe(List.of(), 100));
    }
    @Test void firstLayerCoversCityAndAllTopFiveAreSplit() {
        properties.getPoiGrid().setInitialGridSizeMeter(1000);
        List<GridCell> requests = new ArrayList<>();
        when(client.poiCount(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            GridCell g = inv.getArgument(2); requests.add(g);
            boolean coarse = g.maxLat() - g.minLat() > GeoUtils.meterToLatDegree(750);
            int count = coarse && g.maxLng() < .04 && g.maxLat() < .02 ? 300 : 30;
            BiConsumer<Integer, BusinessException> callback = inv.getArgument(3); callback.accept(count, null); return count;
        });
        var city = GeometryUtils.parsePolyline("0,0;0.04,0;0.04,0.02;0,0.02");
        var result = service.probeRecommendedGridSize("batch", "310000", "050000", city);
        int coarse = (int)GridUtils.cover(city.getEnvelopeInternal(), 1000).stream().filter(g -> GeometryUtils.relationType(city, g) != 0).count();
        assertEquals(coarse + 20, requests.size());
        assertEquals(500, result.recommendedGridSize()); assertEquals(30, result.maxProbeCount());
        verify(recorder, times(requests.size())).saveAttempt(any());
        // 本轮孩子不按父分组晋级，排序统一由 selectTop 测试验证。
    }
    @Test void safeAtFirstLayerStopsImmediately() {
        mockCounts(92);
        var result = service.probeRecommendedGridSize("batch", "310000", "050000", smallCity());
        assertEquals(5000, result.recommendedGridSize()); assertEquals(92, result.maxProbeCount());
        verify(client, times(1)).poiCount(anyString(), anyString(), any(), any());
    }
    @Test void minimumStopsEvenWhenUnsafe() {
        properties.getPoiGrid().setInitialGridSizeMeter(500);
        properties.getPoiGrid().setMinGridSizeMeter(250);
        mockCounts(200);
        var result = service.probeRecommendedGridSize("batch", "310000", "050000", smallCity());
        assertEquals(250, result.recommendedGridSize()); assertEquals(200, result.maxProbeCount());
        ArgumentCaptor<AmapGridProbe> probes = ArgumentCaptor.forClass(AmapGridProbe.class);
        verify(recorder, atLeastOnce()).saveAttempt(probes.capture());
        assertTrue(probes.getAllValues().stream().allMatch(p -> p.getProbeLevel() <= 1));
    }
    @Test void clampedMinimumIsActuallyProbedAt250Meters() {
        properties.getPoiGrid().setInitialGridSizeMeter(625);
        mockCounts(200);
        var result = service.probeRecommendedGridSize("batch", "310000", "050000", smallCity());
        assertEquals(250, result.recommendedGridSize());
        ArgumentCaptor<AmapGridProbe> records = ArgumentCaptor.forClass(AmapGridProbe.class);
        verify(recorder, atLeastOnce()).saveAttempt(records.capture());
        assertTrue(records.getAllValues().stream().anyMatch(p -> p.getGridSizeMeter() == 156));
        var minimum = records.getAllValues().stream().filter(p -> p.getGridSizeMeter() == 250).toList();
        assertFalse(minimum.isEmpty());
        assertTrue(minimum.stream().allMatch(p -> Math.abs(p.getMaxLat().doubleValue() - p.getMinLat().doubleValue() - GeoUtils.meterToLatDegree(250)) < 1e-12));
    }
    @Test void failedRequestRecordIsSavedAndCannotBecomeZero() {
        when(client.poiCount(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            BusinessException failure = new BusinessException("高德 HTTP 状态异常: 500");
            BiConsumer<Integer, BusinessException> callback = inv.getArgument(3); callback.accept(null, failure); throw failure;
        });
        assertThrows(BusinessException.class, () -> service.probeRecommendedGridSize("batch", "310000", "050000", smallCity()));
        ArgumentCaptor<AmapGridProbe> record = ArgumentCaptor.forClass(AmapGridProbe.class);
        verify(recorder).saveAttempt(record.capture());
        assertEquals("FAILED", record.getValue().getProbeStatus()); assertNull(record.getValue().getPoiCount());
    }
    private org.locationtech.jts.geom.Geometry smallCity() { return GeometryUtils.parsePolyline("0,0;0.002,0;0.002,0.002;0,0.002"); }
    private void mockCounts(int count) {
        when(client.poiCount(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            BiConsumer<Integer, BusinessException> callback = inv.getArgument(3); callback.accept(count, null); return count;
        });
    }
}
