package com.example.amap.config;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {
    @NotBlank private String key;
    @NotBlank private String baseUrl = "https://restapi.amap.com";
    @Min(1) private int connectTimeoutSeconds = 10;
    @Min(1) private int readTimeoutSeconds = 30;
    @Valid @NotNull private PoiGrid poiGrid = new PoiGrid();
    @Data
    public static class PoiGrid {
        private String region;
        private String types;
        @AssertTrue(message = "city-limit 必须为 true") private boolean cityLimit = true;
        private boolean showFields;
        @Min(1) private int initialGridSizeMeter = 5000;
        @Min(0) private int safeCount = 100;
        @Min(1) private int minGridSizeMeter = 250;
        @Min(1) private int topDensityGridCount = 5;
        @Min(1) @Max(25) private int pageSize = 25;
        @Min(1) private long requestIntervalMs = 1100;
        @Min(0) @Max(20) private int retryCount = 3;
        @NotEmpty private List<@NotNull @Min(0) Long> retryBackoffMs = List.of(1000L, 3000L, 10000L);
        @Min(1) @Max(1000) private int databaseBatchSize = 500;
        @AssertTrue(message = "initial-grid-size-meter 必须大于等于 min-grid-size-meter")
        public boolean isGridSizeValid() { return initialGridSizeMeter >= minGridSizeMeter; }
    }
}
