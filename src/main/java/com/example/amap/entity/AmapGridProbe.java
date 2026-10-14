package com.example.amap.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@TableName("amap_grid_probe")
public class AmapGridProbe {
    @TableId(type = IdType.AUTO) private Long id;
    private String batchNo;
    private String region;
    private String poiType;
    private Integer gridSizeMeter;
    private BigDecimal minLng;
    private BigDecimal minLat;
    private BigDecimal maxLng;
    private BigDecimal maxLat;
    private BigDecimal centerLng;
    private BigDecimal centerLat;
    private Integer poiCount;
    private Integer probeLevel;
    private String probeStatus;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;

}
