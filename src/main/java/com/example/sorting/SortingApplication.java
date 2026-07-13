package com.example.sorting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.sorting.repository")
public class SortingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SortingApplication.class, args);
    }

}
