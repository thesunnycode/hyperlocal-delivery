package com.hyperlocal.delivery;

import java.util.TimeZone;

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
        // {@code TimeUtils}'s class doc — and every {@code @CreationTimestamp}/
        // {@code @UpdateTimestamp} column, and every bare {@code LocalDateTime.now()}
        // call across the services — assumes a LocalDateTime is always a UTC
        // instant. That assumption is only true if the JVM's default zone IS
        // UTC; otherwise `LocalDateTime.now()` returns the *host's* local
        // wall-clock digits, which then get re-stamped with a "Z" suffix by
        // TimeUtils.toIso and shipped to the browser as if they were UTC —
        // silently shifting every timestamp (createdAt, deliveredAt, invite
        // and OTP expiries, password-changed-at…) by the host's UTC offset.
        // Pin the default zone before Spring, Hibernate or any date-time
        // class loads, so the invariant the rest of the codebase already
        // documents and relies on is actually true.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(DeliveryApplication.class, args);
    }
}
