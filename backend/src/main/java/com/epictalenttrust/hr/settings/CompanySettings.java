package com.epictalenttrust.hr.settings;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A client's screening settings: shortlist and reserve marks, and the limits for gap and short-job flags. */
@Entity
@Table(name = "company_settings")
public class CompanySettings extends AuditedEntity {

    public static final int DEFAULT_SHORTLIST = 70;
    public static final int DEFAULT_RESERVE = 50;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "shortlist_cutoff", nullable = false)
    private int shortlistCutoff = DEFAULT_SHORTLIST;

    @Column(name = "reserve_cutoff", nullable = false)
    private int reserveCutoff = DEFAULT_RESERVE;

    @Column(name = "gap_flag_months", nullable = false)
    private int gapFlagMonths = 6;

    @Column(name = "short_job_months", nullable = false)
    private int shortJobMonths = 12;

    protected CompanySettings() {
    }

    public CompanySettings(UUID companyId) {
        this.companyId = companyId;
    }

    public void update(int shortlistCutoff, int reserveCutoff, int gapFlagMonths, int shortJobMonths) {
        this.shortlistCutoff = shortlistCutoff;
        this.reserveCutoff = reserveCutoff;
        this.gapFlagMonths = gapFlagMonths;
        this.shortJobMonths = shortJobMonths;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public int getShortlistCutoff() {
        return shortlistCutoff;
    }

    public int getReserveCutoff() {
        return reserveCutoff;
    }

    public int getGapFlagMonths() {
        return gapFlagMonths;
    }

    public int getShortJobMonths() {
        return shortJobMonths;
    }
}
