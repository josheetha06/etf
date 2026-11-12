package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A private HR note about a candidate in one job. Only people at the client company see it. */
@Entity
@Table(name = "application_notes")
public class ApplicationNote extends AuditedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    protected ApplicationNote() {
    }

    public ApplicationNote(UUID applicationId, UUID authorId, String body) {
        this.applicationId = applicationId;
        this.authorId = authorId;
        this.body = body;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }
}
