package com.example.amap.service.impl;
import com.example.amap.client.AmapClient;
import com.example.amap.exception.BusinessException;
import com.example.amap.service.AmapDistrictService;
import com.example.amap.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapDistrictServiceImpl implements AmapDistrictService {
    private final AmapClient client;
    @Override public Geometry getCityGeometry(String region) {
        var district = client.district(region).getDistricts().stream()
            .filter(x -> region.equals(x.getAdcode())).findFirst()
            .orElseThrow(() -> new BusinessException("高德未返回匹配 adcode 的行政区"));
        Geometry geometry = GeometryUtils.parsePolyline(district.getPolyline());
        log.info("[District] region={} polygons={}", region, geometry.getNumGeometries());
        return geometry;
    }
}
