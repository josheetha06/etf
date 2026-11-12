package com.epictalenttrust.hr.staff;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ett_staff")
public class EttStaff extends AuditedEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private EttStaffRole role;

    @Column(nullable = false)
    private EttStaffStatus status = EttStaffStatus.ACTIVE;

    protected EttStaff() {
    }

    public EttStaff(String fullName, String email, EttStaffRole role) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public EttStaffRole getRole() {
        return role;
    }

    public EttStaffStatus getStatus() {
        return status;
    }
}
