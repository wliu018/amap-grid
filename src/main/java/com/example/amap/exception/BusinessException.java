package com.example.amap.exception;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus httpStatus;
    public BusinessException(String message) { this(HttpStatus.BAD_GATEWAY, message); }
    public BusinessException(HttpStatus status, String message) { super(message); this.httpStatus = status; }
}
