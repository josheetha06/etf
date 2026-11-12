package com.epictalenttrust.hr.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    Optional<JobApplication> findByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    List<JobApplication> findByJobId(UUID jobId);

    long countByJobId(UUID jobId);

    @Query("""
            select a from JobApplication a, com.epictalenttrust.hr.job.Job j
             where a.jobId = j.id and a.cvDeleteAfter < :today and a.cvFileKey is not null
               and (a.status <> com.epictalenttrust.hr.application.ApplicationStatus.ACTIVE
                    or j.status in (com.epictalenttrust.hr.job.JobStatus.CLOSED, com.epictalenttrust.hr.job.JobStatus.ARCHIVED))
            """)
    List<JobApplication> findCvsDueForDeletion(LocalDate today);
}
