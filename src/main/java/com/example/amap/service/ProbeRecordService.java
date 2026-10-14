package com.example.amap.service;
import com.example.amap.entity.AmapGridProbe;
import com.example.amap.mapper.AmapGridProbeMapper;
import com.example.amap.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@RequiredArgsConstructor
public class ProbeRecordService {
    private final AmapGridProbeMapper mapper;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAttempt(AmapGridProbe probe) {
        try {
            if (mapper.insert(probe) != 1) throw new IllegalStateException("探测记录未保存");
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "探测记录数据库写入失败");
        }
    }
}
