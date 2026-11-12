package com.epictalenttrust.hr.hruser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrUserRepository extends JpaRepository<HrUser, UUID> {

    @Query("select u from HrUser u where lower(u.workEmail) = lower(:email)")
    Optional<HrUser> findByEmail(String email);

    List<HrUser> findByCompanyIdOrderByFullName(UUID companyId);
}
