package com.epictalenttrust.hr.notification;

import com.epictalenttrust.hr.common.Rows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Once a day, one summary email for HR users who chose a daily summary. */
@Component
@ConditionalOnProperty(prefix = "ett.scheduling", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDigestJob {

    private final JdbcClient jdbc;
    private final NotificationMailer mailer;

    public NotificationDigestJob(JdbcClient jdbc, NotificationMailer mailer) {
        this.jdbc = jdbc;
        this.mailer = mailer;
    }

    @Scheduled(cron = "0 13 8 * * *", zone = "Asia/Kolkata")
    public void sendDailySummaries() {
        record User(UUID id, String email, String name) {
        }
        List<User> users = jdbc.sql("""
                        select u.id, u.work_email, u.full_name from hr_users u
                          join hr_user_preferences p on p.hr_user_id = u.id
                         where p.email_alerts = 'daily' and u.status <> 'disabled'
                           and not exists (select 1 from leave_cover lc join companies co on co.id = lc.company_id
                                                  where lc.user_id = u.id and lc.ended_early_at is null
                                                    and (now() at time zone co.time_zone)::date between lc.starts_on and lc.ends_on)
                        """)
                .query((rs, row) -> new User(Rows.uuid(rs, "id"), rs.getString("work_email"), rs.getString("full_name")))
                .list();
        for (User user : users) {
            record Item(UUID id, String title, String link) {
            }
            List<Item> items = jdbc.sql("""
                            select id, title, link from notifications
                             where recipient_id = :u and emailed_at is null and read_at is null
                             order by created_at limit 50
                            """)
                    .param("u", user.id())
                    .query((rs, row) -> new Item(Rows.uuid(rs, "id"), rs.getString("title"), rs.getString("link")))
                    .list();
            if (items.isEmpty()) {
                continue;
            }
            StringBuilder text = new StringBuilder("Hello " + NotificationMailer.firstName(user.name())
                    + ",\n\nHere's what happened in the ETT HR portal since your last summary:\n\n");
            List<UUID> ids = new ArrayList<>();
            for (Item item : items) {
                text.append("• ").append(item.title()).append("\n  ").append(mailer.url(item.link())).append("\n");
                ids.add(item.id());
            }
            text.append("\nYou can change how often you get these emails in Settings.");
            mailer.send(user.email(), "ETT HR portal: your daily summary (" + items.size() + ")", text.toString(), ids);
        }
    }
}
