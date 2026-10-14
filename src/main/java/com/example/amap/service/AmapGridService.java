package com.example.amap.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.amap.entity.AmapGrid;
import org.locationtech.jts.geom.Geometry;
import java.util.List;

public interface AmapGridService extends IService<AmapGrid> {
    List<AmapGrid> generateCityGrids(String region, String types, Geometry city, int meter);
    void randomizeOrder(List<AmapGrid> grids);
    void saveGridBatch(List<AmapGrid> grids);
}
