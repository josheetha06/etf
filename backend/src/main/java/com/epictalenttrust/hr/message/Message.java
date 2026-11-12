package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An email to or from a candidate, kept with the application so HR sees the whole conversation. */
@Entity
@Table(name = "messages")
public class Message extends AuditedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "interview_id")
    private UUID interviewId;

    @Column(nullable = false)
    private MessageChannel channel = MessageChannel.EMAIL;

    @Column(name = "sender_kind", nullable = false)
    private SenderKind senderKind;

    @Column(name = "sender_id")
    private UUID senderId;

    @Column(name = "to_hr_user_id")
    private UUID toHrUserId;

    @Column(nullable = false)
    private MessageDirection direction;

    private String subject;

    private String body;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Message() {
    }

    public static Message outbound(UUID applicationId, UUID interviewId, SenderKind senderKind, UUID senderId,
                                   String subject, String body) {
        Message message = new Message();
        message.applicationId = applicationId;
        message.interviewId = interviewId;
        message.senderKind = senderKind;
        message.senderId = senderId;
        message.direction = MessageDirection.OUTBOUND;
        message.subject = subject;
        message.body = body;
        return message;
    }

    public static Message inbound(UUID applicationId, UUID interviewId, UUID candidateId, UUID toHrUserId,
                                  String subject, String body, Instant receivedAt) {
        Message message = new Message();
        message.applicationId = applicationId;
        message.interviewId = interviewId;
        message.senderKind = SenderKind.CANDIDATE;
        message.senderId = candidateId;
        message.toHrUserId = toHrUserId;
        message.direction = MessageDirection.INBOUND;
        message.subject = subject;
        message.body = body;
        message.sentAt = receivedAt;
        return message;
    }

    public void markSent(Instant at) {
        this.sentAt = at;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getInterviewId() {
        return interviewId;
    }

    public SenderKind getSenderKind() {
        return senderKind;
    }

    public MessageDirection getDirection() {
        return direction;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public UUID getToHrUserId() {
        return toHrUserId;
    }
}
