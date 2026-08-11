package com.hyperlocal.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.hyperlocal.delivery.security.JwtProperties;

/**
 * Spring Boot entry point for the Hyperlocal Delivery Backend.
 *
 * <p>Enables typed configuration binding for {@link JwtProperties}.
 * Other {@code @ConfigurationProperties} records are activated where
 * they are consumed (see {@code SecurityConfig}).
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class DeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class, args);
    }
}
