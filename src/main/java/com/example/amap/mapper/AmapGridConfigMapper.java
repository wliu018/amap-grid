package com.example.amap.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.amap.entity.AmapGridConfig;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface AmapGridConfigMapper extends BaseMapper<AmapGridConfig> {
    int upsert(AmapGridConfig config);
}
