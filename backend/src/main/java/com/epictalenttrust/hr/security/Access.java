package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.common.TimeZones;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Which jobs the signed-in person can see. HR admins see every job in the company; recruiters and hiring managers
 * see the jobs they own or are named on as hiring manager, plus those of anyone they are covering while on leave.
 */
@Component
public class Access {

    /** The people whose jobs are visible: the signed-in person first, then anyone they are covering today. */
    public record Scope(boolean allJobs, List<UUID> people, List<UUID> covering) {
    }

    private static final String CACHE_KEY = Access.class.getName() + ".scope";

    private final JdbcClient jdbc;
    private final Clock clock;

    public Access(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Scope scope() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST) instanceof Scope cached) {
            return cached;
        }
        HrUserPrincipal me = CurrentUser.get();
        List<UUID> covering = coveringToday(me.id(), me.companyId());
        List<UUID> people = new ArrayList<>();
        people.add(me.id());
        people.addAll(covering);
        Scope scope = new Scope(me.role().seesAllJobs(), List.copyOf(people), covering);
        if (request != null) {
            request.setAttribute(CACHE_KEY, scope, RequestAttributes.SCOPE_REQUEST);
        }
        return scope;
    }

    /** Adds the company and visibility parameters every pipeline or job query needs. */
    public JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec) {
        Scope scope = scope();
        return spec.param("companyId", CurrentUser.companyId())
                .param("allJobs", scope.allJobs())
                .param("people", scope.people());
    }

    /** True if this job is visible to the signed-in person. */
    public boolean canSee(UUID ownerId, UUID hiringManagerId) {
        Scope scope = scope();
        return scope.allJobs() || scope.people().contains(ownerId)
                || (hiringManagerId != null && scope.people().contains(hiringManagerId));
    }

    /**
     * If the signed-in person can only see this job because they are covering for its owner or hiring manager,
     * the person they are covering for (recorded in the audit trail as "covering for …"); otherwise null.
     */
    public UUID coveringFor(UUID ownerId, UUID hiringManagerId) {
        Scope scope = scope();
        UUID me = CurrentUser.id();
        if (me.equals(ownerId) || me.equals(hiringManagerId)) {
            return null;
        }
        if (scope.covering().contains(ownerId)) {
            return ownerId;
        }
        if (hiringManagerId != null && scope.covering().contains(hiringManagerId)) {
            return hiringManagerId;
        }
        return null;
    }

    /** Anyone this person is covering today (in the company's own time zone). */
    public List<UUID> coveringToday(UUID userId, UUID companyId) {
        LocalDate today = LocalDate.now(clock.withZone(companyZone(companyId)));
        return jdbc.sql("""
                        select user_id from leave_cover
                         where cover_user_id = :me and company_id = :company and ended_early_at is null
                           and :today between starts_on and ends_on
                        """)
                .param("me", userId).param("company", companyId).param("today", today)
                .query(UUID.class).list();
    }

    private java.time.ZoneId companyZone(UUID companyId) {
        String zone = jdbc.sql("select time_zone from companies where id = :id").param("id", companyId)
                .query(String.class).optional().orElse(null);
        return TimeZones.parseOr(zone, TimeZones.INDIA);
    }
}
