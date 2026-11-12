package com.epictalenttrust.hr.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JobCriterionRepository extends JpaRepository<JobCriterion, UUID> {

    List<JobCriterion> findByJobIdOrderByKindAscCreatedAtAsc(UUID jobId);

    /** The criteria in use now (older versions are kept, retired). */
    List<JobCriterion> findByJobIdAndRetiredAtIsNullOrderByKindAscCreatedAtAsc(UUID jobId);

    @Modifying
    @Query("delete from JobCriterion c where c.jobId = :jobId")
    void deleteByJobId(UUID jobId);
}
