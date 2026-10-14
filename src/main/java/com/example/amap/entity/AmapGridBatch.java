package com.example.amap.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@TableName("amap_grid_batch")
public class AmapGridBatch {
    @TableId(type = IdType.AUTO) private Long id;
    private String batchNo;
    private String region;
    private String poiType;
    private Integer recommendedGridSize;
    private Integer safeCount;
    private Integer maxProbeCount;
    private Integer gridCount;
    private Integer insideGridCount;
    private Integer boundaryGridCount;
    private String status;
    private String errorMessage;
    private LocalDateTime finishTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;

}
