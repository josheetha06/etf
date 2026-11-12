package com.epictalenttrust.hr.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchEvidenceRepository extends JpaRepository<MatchEvidence, UUID> {

    List<MatchEvidence> findByScreeningId(UUID screeningId);
}
