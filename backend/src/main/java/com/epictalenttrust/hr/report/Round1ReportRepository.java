package com.epictalenttrust.hr.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface Round1ReportRepository extends JpaRepository<Round1Report, UUID> {

    Optional<Round1Report> findByInterviewId(UUID interviewId);
}
