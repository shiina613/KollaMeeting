package com.example.kolla.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Separate configuration class for JPA Auditing.
 * Keeping @EnableJpaAuditing in its own @Configuration class (rather than on
 * the main @SpringBootApplication class) allows @WebMvcTest slices to exclude
 * it without triggering "JPA metamodel must not be empty" errors.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
