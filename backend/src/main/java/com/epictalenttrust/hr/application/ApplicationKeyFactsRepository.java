package com.epictalenttrust.hr.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationKeyFactsRepository extends JpaRepository<ApplicationKeyFacts, UUID> {

    Optional<ApplicationKeyFacts> findByApplicationId(UUID applicationId);
}
