package com.example.amap.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.amap.entity.AmapGridConfig;
import com.example.amap.mapper.AmapGridConfigMapper;
import com.example.amap.service.AmapGridConfigService;
import org.springframework.stereotype.Service;
@Service
public class AmapGridConfigServiceImpl extends ServiceImpl<AmapGridConfigMapper, AmapGridConfig> implements AmapGridConfigService {
    @Override public void upsert(AmapGridConfig config) {
        if (baseMapper.upsert(config) != 1) throw new IllegalStateException("推荐配置 UPSERT 失败");
    }
}
