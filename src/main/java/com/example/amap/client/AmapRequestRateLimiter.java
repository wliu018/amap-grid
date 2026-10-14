package com.example.amap.client;
import com.example.amap.config.AmapProperties;
import com.example.amap.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
/** 整个进程所有高德调用共享锁；锁覆盖 HTTP 执行，避免多个线程获取许可后乱序发起请求。 */
@Component
public class AmapRequestRateLimiter {
    private final long intervalNanos;
    private long lastStart;
    private boolean started;
    public AmapRequestRateLimiter(AmapProperties properties) {
        intervalNanos = TimeUnit.MILLISECONDS.toNanos(properties.getPoiGrid().getRequestIntervalMs());
    }
    public synchronized <T> T execute(Supplier<T> operation) {
        while (started) {
            long remaining = intervalNanos - (System.nanoTime() - lastStart);
            if (remaining <= 0) break;
            try { TimeUnit.NANOSECONDS.sleep(remaining); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "高德请求限速等待被中断");
            }
        }
        started = true;
        lastStart = System.nanoTime();
        return operation.get();
    }
}
