package com.example.amap.exception;
import com.example.amap.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(ApiResponse.error(e.getHttpStatus().value(), e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField() + ": " + x.getDefaultMessage()).sorted().reduce((a,b) -> a + "; " + b).orElse("参数错误");
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> malformed(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求 JSON 格式错误"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        // 不记录原始消息/堆栈，避免 HTTP URL 或数据库连接信息泄露凭证。
        log.error("[GridInit] unexpected failure exceptionType={}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiResponse.error(500, "处理失败，请查看批次状态与服务日志"));
    }
}
