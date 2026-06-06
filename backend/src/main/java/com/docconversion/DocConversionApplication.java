package com.docconversion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DocConversionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocConversionApplication.class, args);
    }
}
