package com.epictalenttrust.hr.candidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Candidates added before locations were stored get theirs from the CV once, at start-up. Candidates whose CV gives
 * no location are skipped quickly (there is nothing to read).
 */
@Component
@Order(50)
public class CandidateLocationBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CandidateLocationBackfill.class);

    private final JdbcClient jdbc;
    private final CandidateLocationService locations;

    public CandidateLocationBackfill(JdbcClient jdbc, CandidateLocationService locations) {
        this.jdbc = jdbc;
        this.locations = locations;
    }

    @Override
    public void run(ApplicationArguments args) {
        record Pending(UUID candidateId, UUID applicationId) {
        }
        List<Pending> pending = jdbc.sql("""
                        select c.id as candidate_id,
                               (select a.id from applications a where a.candidate_id = c.id order by a.created_at desc limit 1) as application_id
                          from candidates c
                         where c.location_source is null
                           and (c.city is not null or exists (select 1 from applications a join application_key_facts k
                                  on k.application_id = a.id where a.candidate_id = c.id
                                 and (k.current_location is not null or cardinality(k.preferred_locations) > 0)))
                         limit 5000
                        """)
                .query((rs, n) -> new Pending(rs.getObject("candidate_id", UUID.class), rs.getObject("application_id", UUID.class)))
                .list();
        int done = 0;
        for (Pending p : pending) {
            try {
                locations.fromCv(p.candidateId(), p.applicationId());
                done++;
            } catch (RuntimeException ex) {
                log.warn("Couldn't read the location for candidate {}: {}", p.candidateId(), ex.getMessage());
            }
        }
        if (done > 0) {
            log.info("Read the location from the CV for {} existing candidates", done);
        }
    }
}
