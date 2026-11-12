package com.epictalenttrust.hr.notification;

import com.epictalenttrust.hr.notification.NotificationService.View;
import com.epictalenttrust.hr.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The notification bell and each person's email alert setting. */
@RestController
public class NotificationController {

    public record Preferences(@NotNull @Pattern(regexp = "instant|daily|off") String emailAlerts) {
    }

    private final NotificationService notifications;
    private final JdbcClient jdbc;

    public NotificationController(NotificationService notifications, JdbcClient jdbc) {
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/notifications")
    public List<View> list(@RequestParam(defaultValue = "false") boolean unread,
                           @RequestParam(defaultValue = "30") int limit) {
        return notifications.list(CurrentUser.id(), unread, limit);
    }

    @GetMapping("/api/notifications/count")
    public Map<String, Integer> count() {
        return Map.of("unread", notifications.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/api/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        notifications.markRead(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> readAll() {
        notifications.markAllRead(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/preferences")
    @Transactional(readOnly = true)
    public Preferences preferences() {
        String value = jdbc.sql("select email_alerts from hr_user_preferences where hr_user_id = :u")
                .param("u", CurrentUser.id()).query(String.class).optional().orElse("instant");
        return new Preferences(value);
    }

    @PutMapping("/api/me/preferences")
    @Transactional
    public Preferences save(@Valid @RequestBody Preferences body) {
        jdbc.sql("""
                insert into hr_user_preferences (hr_user_id, email_alerts, created_by, last_modified_by)
                values (:u, :v, :u, :u)
                on conflict (hr_user_id) do update set email_alerts = excluded.email_alerts,
                    last_modified_by = excluded.last_modified_by
                """)
                .param("u", CurrentUser.id()).param("v", body.emailAlerts()).update();
        return body;
    }
}
