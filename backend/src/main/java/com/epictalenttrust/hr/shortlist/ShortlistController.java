package com.epictalenttrust.hr.shortlist;

import com.epictalenttrust.hr.security.CanDecide;
import com.epictalenttrust.hr.security.CanManageHiring;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ShortlistController {

    private final ShortlistService shortlist;

    public ShortlistController(ShortlistService shortlist) {
        this.shortlist = shortlist;
    }

    public record DecisionRequest(@NotNull ShortlistDecisionType decision, @Size(max = 500) String reason) {
    }

    public record FlagRequest(@Size(max = 500) String note) {
    }

    public record FlagReviewRequest(@NotNull String outcome, @Size(max = 500) String note) {
    }

    @PostMapping("/api/applications/{applicationId}/shortlist-decision")
    @CanDecide
    public ResponseEntity<Void> decide(@PathVariable UUID applicationId, @Valid @RequestBody DecisionRequest body) {
        shortlist.decide(applicationId, body.decision(), body.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/screening-flags/{flagId}/review")
    @CanDecide
    public ResponseEntity<Void> reviewFlag(@PathVariable UUID flagId, @Valid @RequestBody FlagReviewRequest body) {
        shortlist.reviewFlag(flagId, body.outcome(), body.note());
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/api/jobs/{jobId}/shortlist/finish-review", "/api/jobs/{jobId}/shortlist/confirm"})
    @CanManageHiring
    public ResponseEntity<Void> finishReview(@PathVariable UUID jobId) {
        shortlist.finishReview(jobId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/match-evidence/{evidenceId}/flag")
    public ResponseEntity<Void> flag(@PathVariable UUID evidenceId, @Valid @RequestBody FlagRequest body) {
        shortlist.flagEvidence(evidenceId, body.note());
        return ResponseEntity.noContent().build();
    }
}
