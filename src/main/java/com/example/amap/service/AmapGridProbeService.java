package com.example.amap.service;
import com.example.amap.model.ProbeResult;
import com.example.amap.entity.AmapGridProbe;
import com.baomidou.mybatisplus.extension.service.IService;
import org.locationtech.jts.geom.Geometry;
public interface AmapGridProbeService extends IService<AmapGridProbe> {
    ProbeResult probeRecommendedGridSize(String batchNo, String region, String types, Geometry city);
}
