package com.epictalenttrust.hr.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, UUID> {

    Optional<RescheduleRequest> findFirstByInterviewIdAndStatus(UUID interviewId, RescheduleStatus status);
}
