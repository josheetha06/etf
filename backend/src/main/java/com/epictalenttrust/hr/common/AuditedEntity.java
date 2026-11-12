package com.epictalenttrust.hr.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Primary key plus the six ownership columns every core table carries. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private UUID lastModifiedBy;

    @LastModifiedDate
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "assigned_id")
    private UUID assignedId;

    public UUID getId() {
        return id;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getAssignedId() {
        return assignedId;
    }

    public void setAssignedId(UUID assignedId) {
        this.assignedId = assignedId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return id != null && Objects.equals(id, ((AuditedEntity) other).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
