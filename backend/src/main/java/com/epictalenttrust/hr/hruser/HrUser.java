package com.epictalenttrust.hr.hruser;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hr_users")
public class HrUser extends AuditedEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "work_email", nullable = false)
    private String workEmail;

    @Column(name = "password_hash")
    private String passwordHash;

    /** Encrypted with {@link com.epictalenttrust.hr.security.SecretCipher}; never stored in clear text. */
    @Column(name = "totp_secret")
    private String totpSecret;

    private HrUserRole role;

    @Column(nullable = false)
    private HrUserStatus status = HrUserStatus.INVITED;

    @Column(name = "last_sign_in_at")
    private Instant lastSignInAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    protected HrUser() {
    }

    public HrUser(UUID companyId, String fullName, String workEmail, HrUserRole role) {
        this.companyId = companyId;
        this.fullName = fullName;
        this.workEmail = workEmail;
        this.role = role;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public HrUserRole getRole() {
        return role == null ? HrUserRole.RECRUITER : role;
    }

    public HrUserStatus getStatus() {
        return status;
    }

    public void setStatus(HrUserStatus status) {
        this.status = status;
    }

    public Instant getLastSignInAt() {
        return lastSignInAt;
    }

    public void recordSignIn(Instant at) {
        this.lastSignInAt = at;
        if (status == HrUserStatus.INVITED) {
            status = HrUserStatus.ACTIVE;
        }
    }

    /** A one-time password set by an admin: the person chooses their own on next sign-in. */
    public void setOneTimePassword(String hash) {
        this.passwordHash = hash;
        this.mustChangePassword = true;
    }

    public void changePassword(String hash, Instant at) {
        this.passwordHash = hash;
        this.mustChangePassword = false;
        this.passwordChangedAt = at;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setRole(HrUserRole role) {
        this.role = role;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
