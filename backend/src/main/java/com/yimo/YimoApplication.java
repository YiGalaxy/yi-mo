package com.yimo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yimo.mapper")
public class YimoApplication {

    public static void main(String[] args) {
        SpringApplication.run(YimoApplication.class, args);
    }
}
