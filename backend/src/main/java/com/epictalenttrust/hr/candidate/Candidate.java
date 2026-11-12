package com.epictalenttrust.hr.candidate;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidates")
public class Candidate extends AuditedEntity {

    @Column(name = "candidate_ref", nullable = false, updatable = false)
    private String candidateRef;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String mobile;
    private String city;
    private String country;

    protected Candidate() {
    }

    public Candidate(String candidateRef, String fullName, String email) {
        this.candidateRef = candidateRef;
        this.fullName = fullName;
        this.email = email;
    }

    /** Fills in details we didn't have; never overwrites what another CV already told us. */
    /** HR corrected details that were misread from the CV. */
    public void updateDetails(String fullName, String email, String mobile, String city, String country) {
        this.fullName = fullName;
        this.email = email;
        this.mobile = mobile;
        this.city = city;
        this.country = country;
    }

    public void fillMissing(String mobile, String city, String country) {
        if (this.mobile == null) {
            this.mobile = mobile;
        }
        if (this.city == null) {
            this.city = city;
        }
        if (this.country == null) {
            this.country = country;
        }
    }

    public String getCandidateRef() {
        return candidateRef;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getMobile() {
        return mobile;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }
}
