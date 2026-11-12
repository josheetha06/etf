package com.epictalenttrust.hr.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Writes login_log rows in their own transaction so failures are kept even when sign-in is refused. */
@Component
public class LoginLogWriter {

    public enum Event { SIGN_IN, SIGN_OUT, FAILED_PASSWORD, FAILED_TOTP, LOCKED, PASSWORD_RESET }

    private final JdbcClient jdbc;

    public LoginLogWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String emailAttempted, Event event, boolean success, String failureReason,
                       HttpServletRequest request) {
        jdbc.sql("""
                insert into login_log (user_kind, user_id, email_attempted, event, success, failure_reason, ip_address, user_agent)
                values ('hr_user', :userId, :email, :event, :success, :reason, :ip, :agent)
                """)
                .param("userId", userId)
                .param("email", emailAttempted)
                .param("event", event.name().toLowerCase())
                .param("success", success)
                .param("reason", failureReason)
                .param("ip", request.getRemoteAddr())
                .param("agent", truncate(request.getHeader("User-Agent")))
                .update();
    }

    /** Failed attempts since the later of the window start and the last successful sign-in. */
    @Transactional(readOnly = true)
    public int recentFailures(UUID userId, Instant windowStart) {
        Integer count = jdbc.sql("""
                select count(*) from login_log
                 where user_id = :userId
                   and event in ('failed_password', 'failed_totp')
                   and occurred_at > greatest(:since, coalesce(
                        (select max(occurred_at) from login_log where user_id = :userId
                           and event in ('sign_in', 'password_reset') and success), :since))
                """)
                .param("userId", userId)
                .param("since", java.sql.Timestamp.from(windowStart))
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 400 ? value : value.substring(0, 400);
    }
}
