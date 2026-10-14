package com.example.amap.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@TableName("amap_grid")
public class AmapGrid {
    @TableId(type = IdType.AUTO) private Long id;
    private String batchNo;
    private String region;
    private String poiType;
    private String gridNo;
    private Integer rowIndex;
    private Integer colIndex;
    private BigDecimal minLng;
    private BigDecimal minLat;
    private BigDecimal maxLng;
    private BigDecimal maxLat;
    private BigDecimal centerLng;
    private BigDecimal centerLat;
    private Integer gridSizeMeter;
    private Integer relationType;
    private Long randomOrder;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
