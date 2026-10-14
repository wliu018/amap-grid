package com.example.amap;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.amap.entity.*;
import com.example.amap.mapper.*;
import com.example.amap.model.ProbeResult;
import com.example.amap.service.*;
import com.example.amap.util.GeometryUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 只在专用测试数据库上启用；测试前后清空四张表。CI 启动独立 PostgreSQL service。 */
@SpringBootTest(properties = {"spring.sql.init.mode=always", "amap.key=test-key"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_IT", matches = "true")
class PostgresIntegrationTest {
    @Autowired AmapGridMapper gridMapper;
    @Autowired AmapGridConfigMapper configMapper;
    @Autowired AmapGridBatchMapper batchMapper;
    @Autowired AmapGridProbeMapper probeMapper;
    @Autowired AmapGridService grids;
    @Autowired BatchStateService state;
    @Autowired BatchPersistenceService persistence;
    @Autowired ProbeRecordService recorder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @BeforeEach @AfterEach void clean() {
        gridMapper.delete(Wrappers.lambdaQuery()); probeMapper.delete(Wrappers.lambdaQuery());
        configMapper.delete(Wrappers.lambdaQuery()); batchMapper.delete(Wrappers.lambdaQuery());
    }
    @Test void realPostgresBatchUpsertReuseAndFastjsonMvc() throws Exception {
        AmapGridBatch first = state.create("999999", "050000", 100);
        List<AmapGrid> firstGrids = generated();
        persistence.saveNewBatch(first, new ProbeResult(625, 92), firstGrids);
        assertEquals(firstGrids.size(), gridMapper.selectCount(Wrappers.lambdaQuery()).intValue());
        assertEquals("SUCCESS", batchMapper.selectById(first.getId()).getStatus());
        var result = mvc.perform(post("/api/amap/grid/init").contentType(MediaType.APPLICATION_JSON)
            .content("{\"region\":\"999999\",\"types\":\"050000\",\"force\":false}")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        record Data(String batchNo, int recommendedGridSize, int gridCount) { }
        record Response(int code, String message, Data data) { }
        Response response = JSON.parseObject(result, Response.class);
        assertEquals(0, response.code()); assertEquals(first.getBatchNo(), response.data().batchNo());
        assertEquals(firstGrids.size(), response.data().gridCount());
        AmapGridBatch second = state.create("999999", "050000", 100);
        persistence.saveNewBatch(second, new ProbeResult(625, 86), generated());
        var current = configMapper.selectOne(Wrappers.<AmapGridConfig>lambdaQuery().eq(AmapGridConfig::getRegion, "999999"));
        assertEquals(second.getBatchNo(), current.getCurrentBatchNo()); assertEquals(86, current.getMaxProbeCount());
        assertEquals(firstGrids.size() * 2L, gridMapper.selectCount(Wrappers.lambdaQuery()));
        var waiting = gridMapper.selectWaiting(second.getBatchNo(), 2);
        assertEquals(2, waiting.size()); assertEquals(1L, waiting.get(0).getRandomOrder());
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var locked = gridMapper.lockWaiting(second.getBatchNo(), 2);
            assertEquals(2, locked.size());
            gridMapper.update(null, Wrappers.<AmapGrid>lambdaUpdate().eq(AmapGrid::getId, locked.get(0).getId()).set(AmapGrid::getStatus, "RUNNING"));
        });
        mvc.perform(post("/api/amap/grid/init").contentType(MediaType.APPLICATION_JSON)
            .content("{\"region\":\"\"}")).andExpect(status().isBadRequest());
    }
    @Test void failedFinalTransactionPreservesOldCurrentAndIndependentProbe() {
        AmapGridBatch old = state.create("999999", "050000", 100);
        persistence.saveNewBatch(old, new ProbeResult(625, 90), generated());
        AmapGridBatch failed = state.create("999999", "050000", 100);
        AmapGridProbe probe = new AmapGridProbe();
        probe.setBatchNo(failed.getBatchNo()); probe.setRegion("999999"); probe.setPoiType("050000");
        probe.setGridSizeMeter(625); probe.setProbeLevel(0); probe.setProbeStatus("SUCCESS"); probe.setPoiCount(92);
        probe.setMinLng(BigDecimal.ZERO); probe.setMinLat(BigDecimal.ZERO);
        probe.setMaxLng(BigDecimal.ONE); probe.setMaxLat(BigDecimal.ONE);
        probe.setCenterLng(new BigDecimal("0.5")); probe.setCenterLat(new BigDecimal("0.5"));
        recorder.saveAttempt(probe);
        // 故意违反推荐配置的 NOT NULL，在网格插入和批次 SUCCESS 更新后使 UPSERT 失败。
        failed.setSafeCount(null);
        assertThrows(RuntimeException.class, () -> persistence.saveNewBatch(failed, new ProbeResult(625, 92), generated()));
        assertEquals(0L, gridMapper.selectCount(Wrappers.<AmapGrid>lambdaQuery().eq(AmapGrid::getBatchNo, failed.getBatchNo())));
        assertEquals("CREATING", batchMapper.selectById(failed.getId()).getStatus());
        assertEquals(old.getBatchNo(), configMapper.selectOne(Wrappers.lambdaQuery()).getCurrentBatchNo());
        assertEquals(1L, probeMapper.selectCount(Wrappers.lambdaQuery()));
        state.fail(failed.getBatchNo(), "test failure");
        assertEquals("FAILED", batchMapper.selectById(failed.getId()).getStatus());
    }
    private List<AmapGrid> generated() {
        var result = grids.generateCityGrids("999999", "050000",
            GeometryUtils.parsePolyline("121,31;121.02,31;121.02,31.02;121,31.02"), 625);
        grids.randomizeOrder(result); return result;
    }
}
