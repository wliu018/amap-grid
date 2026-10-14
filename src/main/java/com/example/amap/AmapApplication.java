package com.example.amap;
import com.example.amap.config.AmapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication
@EnableConfigurationProperties(AmapProperties.class)
public class AmapApplication {
    public static void main(String[] args) { SpringApplication.run(AmapApplication.class, args); }
}
