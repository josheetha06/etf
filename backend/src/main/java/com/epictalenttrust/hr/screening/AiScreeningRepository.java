package com.epictalenttrust.hr.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiScreeningRepository extends JpaRepository<AiScreening, UUID> {

    Optional<AiScreening> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
