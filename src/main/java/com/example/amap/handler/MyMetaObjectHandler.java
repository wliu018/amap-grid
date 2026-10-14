package com.example.amap.handler;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override public void insertFill(MetaObject m) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(m, "createTime", LocalDateTime.class, now);
        strictInsertFill(m, "updateTime", LocalDateTime.class, now);
    }
    @Override public void updateFill(MetaObject m) { strictUpdateFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now()); }
}
