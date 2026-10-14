package com.example.amap.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.amap.entity.AmapGridBatch;
import com.example.amap.enums.GridBatchStatus;
import com.example.amap.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class BatchStateService {
    private final AmapGridBatchService batches;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AmapGridBatch create(String region, String types, int safeCount) {
        AmapGridBatch batch = new AmapGridBatch();
        batch.setBatchNo(LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
            + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        batch.setRegion(region); batch.setPoiType(types); batch.setSafeCount(safeCount);
        batch.setGridCount(0); batch.setInsideGridCount(0); batch.setBoundaryGridCount(0);
        batch.setStatus(GridBatchStatus.CREATING.name());
        try {
            if (!batches.save(batch)) throw new IllegalStateException("batch 创建失败");
        } catch (RuntimeException e) { throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "batch 创建失败"); }
        return batch;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String batchNo, String message) {
        String safeMessage = message == null ? "初始化失败" : message.substring(0, Math.min(1000, message.length()));
        boolean updated = batches.update(Wrappers.<AmapGridBatch>lambdaUpdate()
            .eq(AmapGridBatch::getBatchNo, batchNo).eq(AmapGridBatch::getStatus, GridBatchStatus.CREATING.name())
            .set(AmapGridBatch::getStatus, GridBatchStatus.FAILED.name())
            .set(AmapGridBatch::getErrorMessage, safeMessage).set(AmapGridBatch::getFinishTime, LocalDateTime.now()));
        if (!updated) throw new IllegalStateException("FAILED 批次状态保存失败");
    }
}
