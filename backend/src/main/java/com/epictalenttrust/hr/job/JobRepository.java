package com.epictalenttrust.hr.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query(value = "select nextval('job_ref_seq')", nativeQuery = true)
    long nextRefNumber();
}
