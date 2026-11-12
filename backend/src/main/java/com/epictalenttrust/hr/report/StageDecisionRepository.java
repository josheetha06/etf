package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.application.ApplicationStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageDecisionRepository extends JpaRepository<StageDecision, UUID> {

    List<StageDecision> findByApplicationIdAndStageOrderByCreatedAtDesc(UUID applicationId, ApplicationStage stage);
}
