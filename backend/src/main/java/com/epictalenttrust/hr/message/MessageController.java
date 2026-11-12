package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.message.MessageQueries.MessageView;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class MessageController {

    private final MessageQueries queries;
    private final MessageRepository messages;
    private final Tenancy tenancy;
    private final Clock clock;

    public MessageController(MessageQueries queries, MessageRepository messages, Tenancy tenancy, Clock clock) {
        this.queries = queries;
        this.messages = messages;
        this.tenancy = tenancy;
        this.clock = clock;
    }

    @GetMapping("/api/messages")
    public List<MessageView> folder(@RequestParam(defaultValue = "inbox") String folder) {
        MessageDirection direction = switch (folder.toLowerCase(Locale.ROOT)) {
            case "sent" -> MessageDirection.OUTBOUND;
            default -> MessageDirection.INBOUND;
        };
        return queries.folder(CurrentUser.companyId(), direction);
    }

    @GetMapping("/api/messages/unread-count")
    public Map<String, Integer> unread() {
        return Map.of("unread", queries.unreadCount(CurrentUser.companyId()));
    }

    @PostMapping("/api/messages/{messageId}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable UUID messageId) {
        UUID applicationId = queries.applicationOf(messageId).orElseThrow(() -> ApiException.notFound("Message"));
        tenancy.application(applicationId);
        messages.findById(messageId).ifPresent(m -> m.markRead(clock.instant()));
        return ResponseEntity.noContent().build();
    }
}
