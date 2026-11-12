package com.epictalenttrust.hr.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, UUID> {
}
