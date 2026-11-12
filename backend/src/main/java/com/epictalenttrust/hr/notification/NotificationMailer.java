package com.epictalenttrust.hr.notification;

import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.mail.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Emails HR users about new notifications, for those who want instant alerts. */
@Component
public class NotificationMailer {

    private static final Logger log = LoggerFactory.getLogger(NotificationMailer.class);

    private final JdbcClient jdbc;
    private final MailGateway gateway;
    private final EttProperties properties;
    private final Clock clock;

    public NotificationMailer(JdbcClient jdbc, MailGateway gateway, EttProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationService.Created event) {
        for (UUID id : event.ids()) {
            record Row(String email, String name, String title, String body, String link, String pref) {
            }
            Row row = jdbc.sql("""
                            select u.work_email, u.full_name, n.title, n.body, n.link,
                                   coalesce(p.email_alerts, 'instant') as pref
                              from notifications n join hr_users u on u.id = n.recipient_id
                              left join hr_user_preferences p on p.hr_user_id = u.id
                             where n.id = :id and n.emailed_at is null
                               -- No emails while on leave: whoever is covering gets them instead.
                               and not exists (select 1 from leave_cover lc join companies co on co.id = lc.company_id
                                                  where lc.user_id = u.id and lc.ended_early_at is null
                                                    and (now() at time zone co.time_zone)::date between lc.starts_on and lc.ends_on)
                            """)
                    .param("id", id)
                    .query((rs, r) -> new Row(rs.getString("work_email"), rs.getString("full_name"), rs.getString("title"),
                            rs.getString("body"), rs.getString("link"), rs.getString("pref")))
                    .optional().orElse(null);
            if (row == null || !"instant".equals(row.pref())) {
                continue;
            }
            String text = "Hello " + firstName(row.name()) + ",\n\n" + row.title()
                    + (row.body() == null ? "" : "\n\n" + row.body())
                    + "\n\nOpen it in the portal: " + url(row.link())
                    + "\n\nYou can change how often you get these emails in Settings.";
            send(row.email(), "ETT HR portal: " + row.title(), text, List.of(id));
        }
    }

    /** Sends one email and records it, then marks the notifications as emailed. */
    void send(String to, String subject, String text, List<UUID> notificationIds) {
        String event;
        String detail = null;
        try {
            gateway.send(to, subject, text, null);
            event = "sent";
        } catch (Exception ex) {
            log.warn("Alert email to {} failed: {}", to, ex.getMessage());
            event = "failed";
            detail = ex.getMessage();
        }
        jdbc.sql("insert into email_log (to_address, provider, event, detail, occurred_at) values (:to, :p, cast(:e as email_event_t), :d, :at)")
                .param("to", to).param("p", gateway.providerName()).param("e", event).param("d", detail)
                .param("at", java.sql.Timestamp.from(clock.instant())).update();
        if ("sent".equals(event)) {
            jdbc.sql("update notifications set emailed_at = :at where id in (:ids)")
                    .param("at", java.sql.Timestamp.from(clock.instant())).param("ids", notificationIds).update();
        }
    }

    String url(String link) {
        String base = properties.portalUrl() == null ? "" : properties.portalUrl().replaceAll("/+$", "");
        return base + (link == null ? "/" : link);
    }

    static String firstName(String fullName) {
        return fullName == null || fullName.isBlank() ? "there" : fullName.strip().split("\\s+")[0];
    }
}
