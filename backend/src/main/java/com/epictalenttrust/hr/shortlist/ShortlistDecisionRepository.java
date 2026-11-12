package com.epictalenttrust.hr.shortlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShortlistDecisionRepository extends JpaRepository<ShortlistDecision, UUID> {

    Optional<ShortlistDecision> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
