package com.epictalenttrust.hr.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    @Query("select c from Candidate c where lower(c.email) = lower(:email)")
    Optional<Candidate> findByEmail(String email);

    @Query(value = "select nextval('candidate_ref_seq')", nativeQuery = true)
    long nextRefNumber();
}
