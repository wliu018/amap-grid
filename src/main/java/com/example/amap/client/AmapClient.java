package com.example.amap.client;
import com.alibaba.fastjson2.JSON;
import com.example.amap.config.AmapProperties;
import com.example.amap.dto.amap.*;
import com.example.amap.exception.BusinessException;
import com.example.amap.model.GridCell;
import com.example.amap.util.GridUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.function.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmapClient {
    private final OkHttpClient http;
    private final AmapProperties properties;
    private final AmapRequestRateLimiter limiter;

    public AmapDistrictResponse district(String region) {
        HttpUrl url = base("/v3/config/district").addQueryParameter("keywords", region)
            .addQueryParameter("subdistrict", "0").addQueryParameter("extensions", "all").build();
        return retry("district", () -> {
            AmapDistrictResponse r = read(url, AmapDistrictResponse.class);
            validate(r == null ? null : r.getStatus(), r == null ? null : r.getInfoCode());
            if (r.getDistricts() == null || r.getDistricts().isEmpty()) throw new BusinessException("高德 district 为空或 adcode 不存在");
            return r;
        }, (value, error) -> { });
    }
    /** 每次实际请求（含重试）回调，供独立保存成功或失败的探测过程记录。 */
    public int poiCount(String region, String types, GridCell grid, BiConsumer<Integer, BusinessException> attemptRecorder) {
        AmapProperties.PoiGrid p = properties.getPoiGrid();
        HttpUrl.Builder builder = base("/v5/place/polygon")
            .addQueryParameter("polygon", GridUtils.buildPolygon(grid))
            .addQueryParameter("types", types).addQueryParameter("region", region)
            .addQueryParameter("city_limit", "true")
            .addQueryParameter("page_size", Integer.toString(p.getPageSize())).addQueryParameter("page_num", "1");
        if (p.isShowFields()) builder.addQueryParameter("show_fields", ""); // 仅基础字段；false 完全省略参数
        HttpUrl url = builder.build();
        return retry("poi", () -> {
            AmapPoiSearchResponse r = read(url, AmapPoiSearchResponse.class);
            validate(r == null ? null : r.getStatus(), r == null ? null : r.getInfoCode());
            return r.requireCount();
        }, attemptRecorder);
    }
    private HttpUrl.Builder base(String path) {
        HttpUrl root = HttpUrl.parse(properties.getBaseUrl());
        if (root == null) throw new BusinessException("高德 base-url 配置无效");
        return root.newBuilder().encodedPath(path).addQueryParameter("key", properties.getKey());
    }
    private <T> T read(HttpUrl url, Class<T> type) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new BusinessException("高德 HTTP 状态异常: " + response.code());
            if (response.body() == null) throw new BusinessException("高德 HTTP body 为空");
            String body = response.body().string();
            if (body.isBlank()) throw new BusinessException("高德 HTTP body 为空");
            try { return JSON.parseObject(body, type); }
            catch (RuntimeException e) { throw new BusinessException("高德 JSON 解析失败"); }
        } catch (IOException e) { throw new BusinessException("高德 HTTP 请求失败"); }
    }
    private void validate(String status, String infoCode) {
        if (!"1".equals(status) || !"10000".equals(infoCode)) {
            // 不直接打印服务端文本，避免服务端回显敏感请求参数。
            String safeCode = infoCode != null && infoCode.matches("[0-9]{5}") ? infoCode : "INVALID";
            throw new BusinessException("高德接口失败 infocode=" + safeCode);
        }
    }
    private <T> T retry(String operation, Supplier<T> action, BiConsumer<T, BusinessException> recorder) {
        int retries = properties.getPoiGrid().getRetryCount();
        for (int attempt = 0; attempt <= retries; attempt++) {
            T value = null;
            BusinessException failure = null;
            try { value = limiter.execute(action); }
            catch (BusinessException e) { failure = e; }
            // 持久化失败必须直接终止，不能误作 HTTP 错误重复请求。
            recorder.accept(value, failure);
            if (failure == null) return value;
            log.warn("[Amap] operation={} attempt={} failure={}", operation, attempt + 1, failure.getMessage());
            if (Thread.currentThread().isInterrupted() || attempt == retries) throw failure;
            backoff(attempt);
        }
        throw new BusinessException("高德请求重试耗尽");
    }
    private void backoff(int attempt) {
        var delays = properties.getPoiGrid().getRetryBackoffMs();
        long delay = delays.get(Math.min(attempt, delays.size() - 1));
        try { Thread.sleep(delay); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "高德重试等待被中断");
        }
    }
}
