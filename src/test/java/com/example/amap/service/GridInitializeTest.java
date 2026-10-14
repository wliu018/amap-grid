package com.example.amap.service;
import com.example.amap.config.AmapProperties;
import com.example.amap.dto.request.GridInitRequest;
import com.example.amap.entity.*;
import com.example.amap.exception.BusinessException;
import com.example.amap.service.impl.GridInitializeServiceImpl;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GridInitializeTest {
    @Test void forceFalseReusesSuccessfulCurrentBatchWithoutHttp() {
        var properties = new AmapProperties();
        properties.getPoiGrid().setRegion("310000"); properties.getPoiGrid().setTypes("050000");
        var district = mock(AmapDistrictService.class); var probe = mock(AmapGridProbeService.class);
        var grids = mock(AmapGridService.class); var configs = mock(AmapGridConfigService.class);
        var batches = mock(AmapGridBatchService.class); var state = mock(BatchStateService.class);
        var persistence = mock(BatchPersistenceService.class);
        var config = new AmapGridConfig(); config.setCurrentBatchNo("existing");
        when(configs.getOne(any())).thenReturn(config);
        var batch = new AmapGridBatch(); batch.setBatchNo("existing"); batch.setRegion("310000"); batch.setPoiType("050000");
        batch.setRecommendedGridSize(625); batch.setGridCount(42); batch.setInsideGridCount(40); batch.setBoundaryGridCount(2);
        when(batches.getOne(any())).thenReturn(batch);
        var service = new GridInitializeServiceImpl(properties, district, probe, grids, configs, batches, state, persistence);
        assertEquals("existing", service.initialize(new GridInitRequest()).batchNo());
        verifyNoInteractions(district, probe, grids, state, persistence);
    }
    @Test void requestParametersOverrideDefaultsAndFailureMarksBatch() {
        var properties = new AmapProperties(); properties.getPoiGrid().setRegion("310000"); properties.getPoiGrid().setTypes("050000");
        var district = mock(AmapDistrictService.class); var probe = mock(AmapGridProbeService.class);
        var grids = mock(AmapGridService.class); var configs = mock(AmapGridConfigService.class);
        var batches = mock(AmapGridBatchService.class); var state = mock(BatchStateService.class);
        var persistence = mock(BatchPersistenceService.class);
        var batch = new AmapGridBatch(); batch.setBatchNo("new");
        when(state.create("110000", "060000", 100)).thenReturn(batch);
        when(district.getCityGeometry("110000")).thenThrow(new BusinessException("行政区查询失败"));
        var request = new GridInitRequest(); request.setRegion("110000"); request.setTypes("060000"); request.setForce(true);
        var service = new GridInitializeServiceImpl(properties, district, probe, grids, configs, batches, state, persistence);
        assertThrows(BusinessException.class, () -> service.initialize(request));
        verify(state).fail("new", "行政区查询失败"); verifyNoInteractions(configs, persistence);
    }
}
