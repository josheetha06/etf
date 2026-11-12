package com.epictalenttrust.hr.activity;

import com.epictalenttrust.hr.common.PgEnumConverter;
import com.epictalenttrust.hr.security.ActorContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Business activity log (table audit_log): "who did what to which record".
 * Field-level history is captured separately by the audit_trail trigger.
 */
@Service
public class ActivityService {

    private final JdbcClient jdbc;

    public ActivityService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String action, String recordType, UUID recordId) {
        recordAs(ActorContext.current(), action, recordType, recordId);
    }

    public void recordAs(ActorContext.Actor actor, String action, String recordType, UUID recordId) {
        jdbc.sql("""
                insert into audit_log (actor_kind, actor_id, action, record_type, record_id, created_by)
                values (:kind, :actorId, :action, :recordType, :recordId, :actorId)
                """)
                .param("kind", PgEnumConverter.toDb(actor.kind()))
                .param("actorId", actor.id())
                .param("action", action)
                .param("recordType", recordType)
                .param("recordId", recordId)
                .update();
    }
}
