package com.example.amap.config;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
@Configuration
@MapperScan("com.example.amap.mapper")
public class MybatisPlusConfig { }
