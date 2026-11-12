package com.epictalenttrust.hr.config;

import com.epictalenttrust.hr.security.ActorContext;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor")
@EnableTransactionManagement
public class PersistenceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Fills created_by / last_modified_by with the acting HR user (or nothing for the system). */
    @Bean
    AuditorAware<UUID> auditor() {
        return () -> Optional.ofNullable(ActorContext.current().id());
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new AuditAwareTransactionManager(entityManagerFactory);
    }
}
