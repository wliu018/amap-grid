package com.example.amap.config;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.*;
import java.time.Duration;
@Configuration
public class OkHttpConfig {
    @Bean
    public OkHttpClient okHttpClient(AmapProperties p) {
        return new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(p.getConnectTimeoutSeconds()))
            .readTimeout(Duration.ofSeconds(p.getReadTimeoutSeconds()))
            .callTimeout(Duration.ofSeconds(p.getConnectTimeoutSeconds() + p.getReadTimeoutSeconds()))
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    }
}
