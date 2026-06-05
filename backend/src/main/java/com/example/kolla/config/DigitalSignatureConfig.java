package com.example.kolla.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DigitalSignatureProperties.class)
public class DigitalSignatureConfig {
}
