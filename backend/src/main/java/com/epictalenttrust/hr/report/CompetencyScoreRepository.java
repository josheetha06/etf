package com.epictalenttrust.hr.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompetencyScoreRepository extends JpaRepository<CompetencyScore, UUID> {
}
