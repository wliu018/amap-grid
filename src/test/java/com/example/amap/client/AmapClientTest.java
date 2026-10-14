package com.example.amap.client;
import com.example.amap.config.*;
import com.example.amap.dto.amap.AmapPoiSearchResponse;
import com.example.amap.exception.BusinessException;
import com.example.amap.model.GridCell;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AmapClientTest {
    private MockWebServer server;
    private AmapProperties properties;
    private AmapClient client;
    @BeforeEach void setup() throws Exception {
        server = new MockWebServer(); server.start();
        properties = new AmapProperties(); properties.setKey("test-key"); properties.setBaseUrl(server.url("/").toString());
        properties.getPoiGrid().setRequestIntervalMs(1); properties.getPoiGrid().setRetryCount(0);
        client = new AmapClient(new OkHttpConfig().okHttpClient(properties), properties, new AmapRequestRateLimiter(properties));
    }
    @AfterEach void close() throws Exception { server.shutdown(); }
    @Test void onlyBasicFieldsAndMandatoryCityParameters() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"status\":\"1\",\"infocode\":\"10000\",\"info\":\"OK\",\"count\":\"92\",\"pois\":[]}"));
        assertEquals(92, query());
        var url = server.takeRequest().getRequestUrl();
        assertNotNull(url); assertEquals("/v5/place/polygon", url.encodedPath());
        assertEquals("true", url.queryParameter("city_limit")); assertEquals("310000", url.queryParameter("region"));
        assertEquals("050000", url.queryParameter("types")); assertEquals("25", url.queryParameter("page_size"));
        assertEquals("1", url.queryParameter("page_num")); assertFalse(url.queryParameterNames().contains("show_fields"));
        assertEquals("121.0,31.0;121.01,31.0;121.01,31.01;121.0,31.01", url.queryParameter("polygon"));
    }
    @Test void districtUsesExactAdcodeAndPolyline() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"status\":\"1\",\"infocode\":\"10000\",\"districts\":[{\"adcode\":\"310000\",\"citycode\":[],\"polyline\":\"0,0;1,0;1,1\"}]}"));
        assertEquals("310000", client.district("310000").getDistricts().get(0).getAdcode());
        assertEquals("all", server.takeRequest().getRequestUrl().queryParameter("extensions"));
    }
    @Test void errorsAreNotZeroAndEveryAttemptIsRecorded() {
        properties.getPoiGrid().setRetryCount(2); properties.getPoiGrid().setRetryBackoffMs(List.of(0L));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setBody("{\"status\":\"1\",\"infocode\":\"10000\",\"count\":\"oops\"}"));
        server.enqueue(new MockResponse().setBody("{\"status\":\"1\",\"infocode\":\"10000\",\"count\":\"0\"}"));
        List<String> attempts = new ArrayList<>();
        int count = client.poiCount("310000", "050000", grid(), (value, error) -> attempts.add(error == null ? "SUCCESS" : "FAILED"));
        assertEquals(0, count); assertEquals(List.of("FAILED", "FAILED", "SUCCESS"), attempts);
    }
    @Test void rejectBadResponses() {
        for (String body : List.of("", "not-json", "null", "{\"status\":\"0\",\"infocode\":\"10001\"}",
            "{\"status\":\"1\",\"infocode\":\"10001\",\"count\":\"0\"}",
            "{\"status\":\"1\",\"infocode\":\"10000\",\"count\":null}",
            "{\"status\":\"1\",\"infocode\":\"10000\",\"count\":\"\"}")) {
            server.enqueue(new MockResponse().setBody(body)); assertThrows(BusinessException.class, this::query);
        }
    }
    @Test void strictCountParsing() {
        AmapPoiSearchResponse response = new AmapPoiSearchResponse();
        for (String bad : Arrays.asList(null, "", "NaN", "-1", "1.5", "2147483648")) {
            response.setCount(bad); assertThrows(BusinessException.class, response::requireCount);
        }
        response.setCount("100"); assertEquals(100, response.requireCount());
    }
    @Test void rateLimiterSerializesConcurrentStarts() throws Exception {
        properties.getPoiGrid().setRequestIntervalMs(35);
        AmapRequestRateLimiter limiter = new AmapRequestRateLimiter(properties);
        var executor = Executors.newFixedThreadPool(3);
        try {
            List<Long> starts = Collections.synchronizedList(new ArrayList<>());
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) futures.add(executor.submit(() -> limiter.execute(() -> { starts.add(System.nanoTime()); return null; })));
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            assertEquals(3, starts.size());
            for (int i = 1; i < starts.size(); i++) assertTrue(starts.get(i) - starts.get(i - 1) >= TimeUnit.MILLISECONDS.toNanos(34));
        } finally { executor.shutdownNow(); }
    }
    private int query() { return client.poiCount("310000", "050000", grid(), (v,e) -> { }); }
    private GridCell grid() { return new GridCell(121, 31, 121.01, 31.01, 1, 1); }
}
