package com.epictalenttrust.hr.settings;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.sample.SampleReportService;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The client's own branding: the logo shown in the portal header (and on the sign-in page when this installation
 * serves one client). Logos live in the company_logos table, one per client.
 */
@Service
public class BrandingService {

    public record Branding(String companyName, boolean hasLogo, Instant logoUpdatedAt, boolean sampleReports) {
    }

    public record Logo(byte[] image, String contentType, Instant updatedAt) {
    }

    private final JdbcClient jdbc;
    private final ActivityService activity;
    private final SampleReportService samples;

    public BrandingService(JdbcClient jdbc, ActivityService activity, SampleReportService samples) {
        this.jdbc = jdbc;
        this.activity = activity;
        this.samples = samples;
    }

    @Transactional(readOnly = true)
    public Branding branding(UUID companyId) {
        String name = jdbc.sql("select name from companies where id = :c").param("c", companyId)
                .query(String.class).optional().orElse(null);
        Instant updated = logoUpdatedAt(companyId);
        return new Branding(name, updated != null, updated, samples.enabled());
    }

    /** Before sign-in: the client's branding only when this installation serves exactly one client. */
    @Transactional(readOnly = true)
    public Optional<UUID> soleCompany() {
        List<UUID> ids = jdbc.sql("select id from companies where status = 'active' limit 2").query(UUID.class).list();
        return ids.size() == 1 ? Optional.of(ids.getFirst()) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<Logo> logo(UUID companyId) {
        return jdbc.sql("select image, content_type, last_modified_at from company_logos where company_id = :c")
                .param("c", companyId)
                .query((rs, row) -> new Logo(rs.getBytes("image"), rs.getString("content_type"),
                        Rows.instant(rs, "last_modified_at")))
                .optional();
    }

    @Transactional
    public Branding saveLogo(byte[] bytes, String fileName) {
        String type = LogoImage.contentType(bytes);
        UUID companyId = CurrentUser.companyId();
        jdbc.sql("""
                insert into company_logos (company_id, content_type, file_name, image, created_by, last_modified_by)
                values (:c, :t, :f, :img, :u, :u)
                on conflict (company_id) do update set content_type = excluded.content_type,
                    file_name = excluded.file_name, image = excluded.image, last_modified_by = excluded.last_modified_by,
                    last_modified_at = now()
                """)
                .param("c", companyId).param("t", type).param("f", clean(fileName)).param("img", bytes)
                .param("u", CurrentUser.id()).update();
        activity.record("logo.updated", "company", companyId);
        return branding(companyId);
    }

    @Transactional
    public Branding removeLogo() {
        UUID companyId = CurrentUser.companyId();
        jdbc.sql("delete from company_logos where company_id = :c").param("c", companyId).update();
        activity.record("logo.removed", "company", companyId);
        return branding(companyId);
    }

    private Instant logoUpdatedAt(UUID companyId) {
        return jdbc.sql("select last_modified_at from company_logos where company_id = :c").param("c", companyId)
                .query((rs, row) -> Rows.instant(rs, "last_modified_at")).optional().orElse(null);
    }

    private static String clean(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.replaceAll("[\\\\/\\p{Cntrl}]", "_").strip();
        return name.length() > 120 ? name.substring(0, 120) : name;
    }
}
