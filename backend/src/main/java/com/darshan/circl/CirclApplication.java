package com.darshan.circl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CirclApplication {

    public static void main(String[] args) {
        SpringApplication.run(CirclApplication.class, args);
    }
}
