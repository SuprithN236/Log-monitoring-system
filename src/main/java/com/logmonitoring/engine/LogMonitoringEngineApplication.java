package com.logmonitoring.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Accounts live in app_users and sign in with JWTs, so Spring Boot's default in-memory user is not wanted.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LogMonitoringEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogMonitoringEngineApplication.class, args);
    }
}
