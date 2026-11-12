package com.epictalenttrust.hr.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    Optional<Interview> findFirstByApplicationIdAndRoundAndStatusNotOrderByCreatedAtDesc(UUID applicationId, int round,
                                                                                         InterviewStatus excluded);

    @Query("""
            select i from Interview i
             where i.scheduledBy = :hrUserId and i.startAt between :from and :to
               and i.status in (com.epictalenttrust.hr.interview.InterviewStatus.DRAFT,
                                com.epictalenttrust.hr.interview.InterviewStatus.INVITED,
                                com.epictalenttrust.hr.interview.InterviewStatus.CONFIRMED,
                                com.epictalenttrust.hr.interview.InterviewStatus.RESCHEDULE_REQUESTED)
            """)
    List<Interview> findBusy(UUID hrUserId, Instant from, Instant to);

    List<Interview> findByApplicationId(UUID applicationId);

    List<Interview> findByApplicationIdAndRoundOrderByCreatedAtDesc(UUID applicationId, int round);

    @Query("""
            select i from Interview i
             where i.status = com.epictalenttrust.hr.interview.InterviewStatus.INVITED
               and i.reminderSentAt is null and i.replyBy between :from and :to
            """)
    List<Interview> findDueForReminder(Instant from, Instant to);
}
