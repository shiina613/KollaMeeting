package com.example.kolla;

import com.example.kolla.config.JaasProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JaasProperties.class)
public class KollaMeetingApplication {

    public static void main(String[] args) {
        SpringApplication.run(KollaMeetingApplication.class, args);
    }
}
