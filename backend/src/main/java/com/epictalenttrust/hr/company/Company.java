package com.epictalenttrust.hr.company;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company extends AuditedEntity {

    @Column(nullable = false)
    private String name;

    private String country;

    @Column(name = "data_region")
    private DataRegion dataRegion;

    @Column(nullable = false)
    private CompanyStatus status = CompanyStatus.ACTIVE;

    protected Company() {
    }

    public Company(String name, String country, DataRegion dataRegion) {
        this.name = name;
        this.country = country;
        this.dataRegion = dataRegion;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public DataRegion getDataRegion() {
        return dataRegion;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyStatus status) {
        this.status = status;
    }

    public boolean canUsePortal() {
        return status == CompanyStatus.ACTIVE;
    }
}
