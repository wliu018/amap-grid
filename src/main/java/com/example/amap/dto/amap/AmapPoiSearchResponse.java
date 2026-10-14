package com.example.amap.dto.amap;
import com.alibaba.fastjson2.annotation.JSONField;
import com.example.amap.exception.BusinessException;
import lombok.Data;
import java.util.List;
@Data
public class AmapPoiSearchResponse {
    private String status;
    private String info;
    @JSONField(name = "infocode") private String infoCode;
    private String count;
    private List<Poi> pois;
    public int requireCount() {
        if (count == null || count.isBlank() || !count.trim().matches("[0-9]+"))
            throw new BusinessException("高德 POI count 为空或非合法非负整数");
        try { return Integer.parseInt(count.trim()); }
        catch (NumberFormatException e) { throw new BusinessException("高德 POI count 超出整数范围"); }
    }
    @Data
    public static class Poi {
        private String id;
        private String name;
        private String type;
        @JSONField(name = "typecode") private String typeCode;
        private String location;
        private String adcode;
    }
}
