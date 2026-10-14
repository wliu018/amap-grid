package com.example.amap.dto.request;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class GridInitRequest {
    @Pattern(regexp = "[0-9]{6}", message = "region 必须为六位行政区 adcode") private String region;
    @Size(max = 100) @Pattern(regexp = "[0-9]{6}(\\|[0-9]{6})*", message = "types 必须为六位分类码，多类型用 | 分隔") private String types;
    private Boolean force = false;
}
