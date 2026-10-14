package com.example.amap.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.amap.entity.AmapGrid;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface AmapGridMapper extends BaseMapper<AmapGrid> {
    int insertBatch(@Param("grids") List<AmapGrid> grids);
    List<AmapGrid> selectWaiting(@Param("batchNo") String batchNo, @Param("limit") int limit);
    /** 必须在事务内调用并更新 RUNNING 后提交，当前阶段没有 Consumer。 */
    List<AmapGrid> lockWaiting(@Param("batchNo") String batchNo, @Param("limit") int limit);
}
