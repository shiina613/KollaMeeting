package com.example.kolla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KollaMeetingApplication {

    public static void main(String[] args) {
        SpringApplication.run(KollaMeetingApplication.class, args);
    }
}
