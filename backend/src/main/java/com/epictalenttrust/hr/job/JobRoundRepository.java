package com.epictalenttrust.hr.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRoundRepository extends JpaRepository<JobRound, UUID> {

    List<JobRound> findByJobIdOrderByRound(UUID jobId);

    Optional<JobRound> findByJobIdAndRound(UUID jobId, int round);

    @Modifying
    @Query("delete from JobRound r where r.jobId = :jobId")
    void deleteByJobId(UUID jobId);
}
