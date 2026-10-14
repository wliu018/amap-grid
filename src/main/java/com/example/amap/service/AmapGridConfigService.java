package com.example.amap.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.amap.entity.AmapGridConfig;

public interface AmapGridConfigService extends IService<AmapGridConfig> {
    void upsert(AmapGridConfig config);
}
