package com.epictalenttrust.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/** HR users sign in through AuthService, so Spring Boot's default in-memory user is switched off. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class EttHrPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(EttHrPortalApplication.class, args);
    }
}
