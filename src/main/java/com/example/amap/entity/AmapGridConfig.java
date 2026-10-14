package com.example.amap.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@TableName("amap_grid_config")
public class AmapGridConfig {
    @TableId(type = IdType.AUTO) private Long id;
    private String region;
    private String poiType;
    private Integer recommendedGridSize;
    private Integer safeCount;
    private Integer maxProbeCount;
    private String currentBatchNo;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
