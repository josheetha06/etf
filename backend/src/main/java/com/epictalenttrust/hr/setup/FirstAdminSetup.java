package com.epictalenttrust.hr.setup;

import com.epictalenttrust.hr.company.Company;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.company.DataRegion;
import com.epictalenttrust.hr.hruser.HrUser;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.hruser.HrUserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

/**
 * Creates the first client company and its admin on a fresh installation, so the portal can be signed in to
 * without touching the database. It does nothing once any HR user exists, so the settings can stay in place.
 * The admin sets up two-step verification on first sign-in.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FirstAdminSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstAdminSetup.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final CompanyRepository companies;
    private final com.epictalenttrust.hr.company.CompanyDefaults defaults;
    private final HrUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final String companyName;
    private final String country;
    private final String dataRegion;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public FirstAdminSetup(CompanyRepository companies, HrUserRepository users, PasswordEncoder passwordEncoder,
                           PlatformTransactionManager transactionManager,
                           com.epictalenttrust.hr.company.CompanyDefaults defaults,
                           @Value("${ett.first-admin.company:}") String companyName,
                           @Value("${ett.first-admin.country:India}") String country,
                           @Value("${ett.first-admin.data-region:apac}") String dataRegion,
                           @Value("${ett.first-admin.name:}") String adminName,
                           @Value("${ett.first-admin.email:}") String adminEmail,
                           @Value("${ett.first-admin.password:}") String adminPassword) {
        this.companies = companies;
        this.defaults = defaults;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
        this.companyName = companyName.strip();
        this.country = country.strip();
        this.dataRegion = dataRegion.strip();
        this.adminName = adminName.strip();
        this.adminEmail = adminEmail.strip().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isEmpty() || users.count() > 0) {
            return;
        }
        if (companyName.isEmpty() || adminName.isEmpty()) {
            throw new IllegalStateException("Set ETT_FIRST_COMPANY and ETT_FIRST_ADMIN_NAME to create the first admin.");
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ETT_FIRST_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        DataRegion region = DataRegion.valueOf(dataRegion.toUpperCase(Locale.ROOT));
        transaction.executeWithoutResult(status -> {
            Company company = companies.save(new Company(companyName, country, region));
            companies.flush();
            defaults.apply(company.getId());
            HrUser admin = new HrUser(company.getId(), adminName, adminEmail, HrUserRole.ADMIN);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            users.save(admin);
        });
        log.info("Created company '{}' and its first admin. Remove ETT_FIRST_ADMIN_PASSWORD from the environment now.", companyName);
    }
}
