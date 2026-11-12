package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Records candidate messages and queues outbound email. */
@Service
public class MessageService {

    private final MessageRepository messages;
    private final CandidateRepository candidates;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MessageService(MessageRepository messages, CandidateRepository candidates, JdbcClient jdbc,
                          ApplicationEventPublisher events, Clock clock) {
        this.messages = messages;
        this.candidates = candidates;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Message emailCandidate(JobApplication application, UUID interviewId, SenderKind senderKind, UUID senderId,
                                  MessageTemplates.Content content) {
        Message message = messages.saveAndFlush(Message.outbound(application.getId(), interviewId, senderKind, senderId,
                content.subject(), content.body()));
        String to = candidates.findById(application.getCandidateId()).map(c -> c.getEmail()).orElse(null);
        jdbc.sql("insert into email_log (message_id, to_address, event) values (:id, :to, 'queued')")
                .param("id", message.getId())
                .param("to", to)
                .update();
        events.publishEvent(new OutboundEmailRequested(message.getId()));
        return message;
    }

    /**
     * Saves an email that goes out after {@code delay} rather than straight away, so HR can still undo the decision
     * behind it. The retry job sends it when the time comes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Message emailCandidateLater(JobApplication application, UUID interviewId, SenderKind senderKind,
                                       UUID senderId, MessageTemplates.Content content, java.time.Duration delay) {
        Message message = messages.saveAndFlush(Message.outbound(application.getId(), interviewId, senderKind, senderId,
                content.subject(), content.body()));
        String to = candidates.findById(application.getCandidateId()).map(c -> c.getEmail()).orElse(null);
        jdbc.sql("update messages set next_attempt_at = :at where id = :id")
                .param("at", java.sql.Timestamp.from(clock.instant().plus(delay))).param("id", message.getId()).update();
        jdbc.sql("insert into email_log (message_id, to_address, event) values (:id, :to, 'queued')")
                .param("id", message.getId()).param("to", to).update();
        return message;
    }

    /** Stops an email that hasn't gone yet. Returns false if it has already been sent. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancelPending(UUID messageId) {
        return jdbc.sql("""
                        update messages set cancelled_at = now(), next_attempt_at = null
                         where id = :id and sent_at is null and cancelled_at is null
                        """)
                .param("id", messageId).update() == 1;
    }

    /** Sends now any of the candidate's emails that are waiting, so they arrive before whatever HR sends next. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releasePending(UUID applicationId) {
        jdbc.sql("""
                        update messages set next_attempt_at = now()
                         where application_id = :a and direction = 'outbound' and sent_at is null and cancelled_at is null
                           and failed_at is null and send_attempts = 0 and next_attempt_at > now()
                         returning id
                        """)
                .param("a", applicationId).query(UUID.class).list()
                .forEach(id -> events.publishEvent(new OutboundEmailRequested(id)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Message recordFromCandidate(JobApplication application, UUID interviewId, UUID toHrUserId,
                                       String subject, String body) {
        return messages.saveAndFlush(Message.inbound(application.getId(), interviewId, application.getCandidateId(),
                toHrUserId, subject, body, clock.instant()));
    }
}
