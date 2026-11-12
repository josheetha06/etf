package com.epictalenttrust.hr.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EttStaffRepository extends JpaRepository<EttStaff, UUID> {
}
