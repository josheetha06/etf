package com.epictalenttrust.hr.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScreeningFlagRepository extends JpaRepository<ScreeningFlag, UUID> {

    List<ScreeningFlag> findByScreeningId(UUID screeningId);
}
