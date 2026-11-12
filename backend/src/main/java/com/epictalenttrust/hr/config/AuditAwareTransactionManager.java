package com.epictalenttrust.hr.config;

import com.epictalenttrust.hr.common.PgEnumConverter;
import com.epictalenttrust.hr.security.ActorContext;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;

/**
 * Tells PostgreSQL who is acting at the start of every transaction, so the
 * audit_trail trigger can record changed_by and actor_kind.
 */
public class AuditAwareTransactionManager extends JpaTransactionManager {

    private static final String SET_ACTOR =
            "select set_config('app.current_user_id', ?, true), set_config('app.current_actor_kind', ?, true)";

    public AuditAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            return;
        }
        ActorContext.Actor actor = ActorContext.current();
        holder.getEntityManager().unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_ACTOR)) {
                statement.setString(1, actor.id() == null ? "" : actor.id().toString());
                statement.setString(2, PgEnumConverter.toDb(actor.kind()));
                statement.execute();
            }
        });
    }
}
