package com.epictalenttrust.hr.company;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** A new company's starting settings: a default round plan and the panel review form's general measures. */
@Component
public class CompanyDefaults {

    private final JdbcClient jdbc;

    public CompanyDefaults(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void apply(UUID companyId) {
        jdbc.sql("""
                insert into round_templates (company_id, round, name, kind, mode, duration_min, target_days)
                select :c, t.round, t.name, t.kind::round_kind_t, t.mode::interview_mode_t, t.minutes, t.days
                  from (values (1, 'Technical Level 1', 'panel', 'online', 60, 3),
                               (2, 'Technical Level 2', 'panel', 'online', 60, 5),
                               (3, 'HR round', 'panel', 'online', 30, 3)) as t(round, name, kind, mode, minutes, days)
                 where not exists (select 1 from round_templates where company_id = :c)
                """).param("c", companyId).update();
        jdbc.sql("""
                insert into review_measures (company_id, name, position)
                select :c, m.name, m.position
                  from (values ('Communication', 1), ('Problem solving', 2), ('Technical depth', 3),
                               ('Attitude and motivation', 4), ('Teamwork', 5), ('Ownership', 6), ('Learning ability', 7),
                               ('Professionalism', 8)) as m(name, position)
                 where not exists (select 1 from review_measures where company_id = :c)
                """).param("c", companyId).update();
        jdbc.sql("""
                insert into decision_reasons (company_id, reason, position)
                select :c, r.reason, r.position
                  from (values ('The role needs deeper hands-on experience in the must-have skills than we saw in this round.', 1),
                               ('Other candidates matched the role''s requirements more closely at this stage.', 2),
                               ('We were looking for clearer problem solving and reasoning in the technical questions.', 3),
                               ('Your notice period is longer than this role can wait for.', 4),
                               ('Your expected salary is above the budget for this role.', 5),
                               ('You weren''t able to attend the interview, and we couldn''t find another time that worked.', 6))
                       as r(reason, position)
                 where not exists (select 1 from decision_reasons where company_id = :c)
                """).param("c", companyId).update();
    }
}
