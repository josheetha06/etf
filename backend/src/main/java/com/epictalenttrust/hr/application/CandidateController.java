package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.screening.AiSuggestion;
import com.epictalenttrust.hr.security.CanManageHiring;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The candidate list and the actions on a candidate's page. */
@RestController
@Validated
public class CandidateController {

    public record NoteRequest(@Size(max = 4000) String body) {
    }

    public record ReasonRequest(@Size(max = 500) String reason) {
    }

    public record OfferRequest(boolean accepted, @Size(max = 500) String note) {
    }

    public record MessageRequest(@Size(max = 200) String subject, @Size(max = 8000) String body) {
    }

    public record AddToJobRequest(@NotNull UUID jobId) {
    }

    private final CandidateDirectory directory;
    private final CandidateService candidates;
    private final TimelineService timeline;

    public CandidateController(CandidateDirectory directory, CandidateService candidates, TimelineService timeline) {
        this.directory = directory;
        this.candidates = candidates;
        this.timeline = timeline;
    }

    @GetMapping("/api/candidates")
    public CandidateDirectory.Page search(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) UUID jobId,
                                          @RequestParam(required = false) ApplicationStage stage,
                                          @RequestParam(required = false) ApplicationStatus status,
                                          @RequestParam(required = false) AiSuggestion suggestion,
                                          @RequestParam(required = false) String decision,
                                          @RequestParam(required = false) ApplicationSource source,
                                          @RequestParam(required = false) String country,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedFrom,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate addedTo,
                                          @RequestParam(defaultValue = "false") boolean needsDecision,
                                          @RequestParam(required = false) String action,
                                          @RequestParam(defaultValue = "action") String sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return directory.search(new CandidateDirectory.Filter(q, jobId, stage, status, suggestion, decision, source,
                country, addedFrom, addedTo, needsDecision, action, sort, page, size));
    }

    @GetMapping("/api/applications/{id}/timeline")
    public List<TimelineService.Event> timeline(@PathVariable UUID id) {
        return timeline.timeline(id);
    }

    @GetMapping("/api/applications/{id}/notes")
    public List<CandidateService.NoteView> notes(@PathVariable UUID id) {
        return candidates.notes(id);
    }

    @PostMapping("/api/applications/{id}/notes")
    @CanManageHiring
    public List<CandidateService.NoteView> addNote(@PathVariable UUID id, @RequestBody NoteRequest body) {
        return candidates.addNote(id, body.body());
    }

    @DeleteMapping("/api/applications/{id}/notes/{noteId}")
    @CanManageHiring
    public List<CandidateService.NoteView> deleteNote(@PathVariable UUID id, @PathVariable UUID noteId) {
        return candidates.deleteNote(id, noteId);
    }

    @GetMapping("/api/applications/{id}/key-facts")
    public CandidateService.KeyFactsView keyFacts(@PathVariable UUID id) {
        return candidates.keyFacts(id);
    }

    @PutMapping("/api/applications/{id}/key-facts")
    @CanManageHiring
    public CandidateService.KeyFactsView updateKeyFacts(@PathVariable UUID id, @RequestBody CandidateService.KeyFactsView body) {
        return candidates.updateKeyFacts(id, body);
    }

    @PutMapping("/api/applications/{id}/candidate")
    @CanManageHiring
    public Map<String, Boolean> updateDetails(@PathVariable UUID id, @RequestBody CandidateService.DetailsInput body) {
        candidates.updateDetails(id, body);
        return Map.of("ok", true);
    }

    @PostMapping("/api/applications/{id}/withdraw")
    @CanManageHiring
    public Map<String, Boolean> withdraw(@PathVariable UUID id, @RequestBody ReasonRequest body) {
        candidates.withdraw(id, body.reason());
        return Map.of("ok", true);
    }

    @PostMapping("/api/applications/{id}/offer-outcome")
    @CanManageHiring
    public Map<String, Boolean> offer(@PathVariable UUID id, @RequestBody OfferRequest body) {
        candidates.recordOffer(id, body.accepted(), body.note());
        return Map.of("ok", true);
    }

    @GetMapping("/api/applications/{id}/message-templates")
    public List<MessageTemplates.Template> templates(@PathVariable UUID id) {
        return candidates.templates(id);
    }

    @PostMapping("/api/applications/{id}/messages")
    @CanManageHiring
    public Map<String, Boolean> send(@PathVariable UUID id, @RequestBody MessageRequest body) {
        candidates.sendMessage(id, body.subject(), body.body());
        return Map.of("ok", true);
    }

    @PostMapping("/api/applications/{id}/add-to-job")
    @CanManageHiring
    public Map<String, UUID> addToJob(@PathVariable UUID id, @RequestBody AddToJobRequest body) {
        return Map.of("applicationId", candidates.addToJob(id, body.jobId()));
    }
}
