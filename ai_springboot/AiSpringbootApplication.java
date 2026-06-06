package com.example.ai_springboot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.ai_springboot.mapper")
public class AiSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSpringbootApplication.class, args);
    }

}
