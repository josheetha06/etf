package com.epictalenttrust.hr.candidateportal;

import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.interview.Interview;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The secure link to a candidate's own page, put in every interview email. No password: the link itself is the key,
 * so only its hash is kept, and it stops working a day after the interview ends.
 */
@Component
public class CandidateLinks {

    private static final Duration AFTER_INTERVIEW = Duration.ofDays(1);
    private static final Duration WITHOUT_INTERVIEW = Duration.ofDays(14);

    private final JdbcClient jdbc;
    private final EttProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CandidateLinks(JdbcClient jdbc, EttProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** A new link for this application (and interview, if the email is about one). */
    public String issue(UUID applicationId, Interview interview) {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expires = interview != null && interview.getStartAt() != null
                ? interview.getStartAt().plus(Duration.ofMinutes(interview.getDurationMin() == null ? 60
                        : interview.getDurationMin())).plus(AFTER_INTERVIEW)
                : clock.instant().plus(WITHOUT_INTERVIEW);
        jdbc.sql("""
                insert into candidate_links (application_id, interview_id, token_hash, expires_at)
                values (:a, :i, :h, :e)
                """)
                .param("a", applicationId).param("i", interview == null ? null : interview.getId())
                .param("h", hash(token)).param("e", Timestamp.from(expires)).update();
        return properties.portalUrl().replaceAll("/+$", "") + "/my-interview/" + token;
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
