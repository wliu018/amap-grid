package com.example.amap.dto.amap;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import java.util.List;
@Data
public class AmapDistrictResponse {
    private String status;
    private String info;
    @JSONField(name = "infocode") private String infoCode;
    private List<District> districts;
    @Data
    public static class District {
        private String adcode;
        private String name;
        private String level;
        private String polyline;
        private String center;
        // 高德省级 citycode 可能返回 []；未使用此字段，保留可变类型以兼容其响应。
        @JSONField(name = "citycode") private Object cityCode;
        private List<District> districts;
    }
}
