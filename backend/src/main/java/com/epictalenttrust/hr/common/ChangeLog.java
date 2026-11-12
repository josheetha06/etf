package com.epictalenttrust.hr.common;

import com.epictalenttrust.hr.job.JobDtos.Changes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who created and last changed records, read from the audit trail the database keeps for every change. This works
 * the same whether a change came from an HR login, a panel member, a partner assessor, the candidate or the system.
 */
@Component
public class ChangeLog {

    /** How the person who made a change is shown, as SQL over {@code audit_trail t} left-joined to {@code hr_users u}. */
    public static final String WHO = """
            case
              when t.actor_kind = 'hr_user' and u.full_name is not null
                then u.full_name || coalesce(' (covering for ' || ob.full_name || ')', '')
              when t.actor_kind in ('panel_member', 'partner_assessor') and t.actor_label is not null then t.actor_label
              when t.actor_kind = 'panel_member' then 'Panel member'
              when t.actor_kind = 'partner_assessor' then 'Partner assessor'
              when t.actor_kind = 'candidate' then 'Candidate'
              when t.actor_kind = 'ett_staff' then 'ETT'
              else 'System'
            end
            """;

    private final JdbcClient jdbc;

    public ChangeLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Created and last-updated details for each record of one table. */
    public Map<UUID, Changes> forRecords(String table, Collection<UUID> ids) {
        return forRecords(List.of(table), ids);
    }

    /**
     * The same, where a record's history spans several tables keyed by the same id (for example a candidate's
     * application row and the candidate row are looked up by their own ids together).
     */
    public Map<UUID, Changes> forRecords(List<String> tables, Collection<UUID> ids) {
        Map<UUID, Changes> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        Map<UUID, Instant[]> times = new HashMap<>();
        Map<UUID, String[]> names = new HashMap<>();
        jdbc.sql("""
                        select distinct on (t.record_id) t.record_id, t.changed_at, %s as who
                          from audit_trail t
                          left join hr_users u on u.id = t.changed_by
                          left join hr_users ob on ob.id = t.on_behalf_of
                         where t.table_name in (:tables) and t.record_id in (:ids) and t.operation = 'INSERT'
                         order by t.record_id, t.id
                        """.formatted(WHO))
                .param("tables", tables).param("ids", ids)
                .query((rs, n) -> remember(rs, times, names, 0))
                .list();
        jdbc.sql("""
                        select distinct on (t.record_id) t.record_id, t.changed_at, %s as who
                          from audit_trail t
                          left join hr_users u on u.id = t.changed_by
                          left join hr_users ob on ob.id = t.on_behalf_of
                         where t.table_name in (:tables) and t.record_id in (:ids)
                         order by t.record_id, t.id desc
                        """.formatted(WHO))
                .param("tables", tables).param("ids", ids)
                .query((rs, n) -> remember(rs, times, names, 1))
                .list();
        for (UUID id : times.keySet()) {
            Instant[] t = times.get(id);
            String[] w = names.get(id);
            out.put(id, new Changes(w[0], t[0], w[1], t[1]));
        }
        return out;
    }

    public Changes forRecord(String table, UUID id) {
        return forRecords(table, List.of(id)).getOrDefault(id, new Changes(null, null, null, null));
    }

    private static int remember(ResultSet rs, Map<UUID, Instant[]> times, Map<UUID, String[]> names, int slot)
            throws SQLException {
        UUID id = Rows.uuid(rs, "record_id");
        times.computeIfAbsent(id, k -> new Instant[2])[slot] = Rows.instant(rs, "changed_at");
        names.computeIfAbsent(id, k -> new String[2])[slot] = rs.getString("who");
        return 0;
    }
}
