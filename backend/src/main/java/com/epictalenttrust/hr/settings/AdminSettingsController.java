package com.epictalenttrust.hr.settings;

import com.epictalenttrust.hr.job.JobDtos;
import com.epictalenttrust.hr.job.JobDtos.RoundInput;
import com.epictalenttrust.hr.routing.CentreAdminService;
import com.epictalenttrust.hr.security.AdminOnly;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Settings tabs HR admins change: company details, the default round plan, the panel review form's measures,
 * partner centres and email wording. Everyone can read them; only HR admins can change them.
 */
@RestController
@RequestMapping("/api/settings")
public class AdminSettingsController {

    public record RoundPlanInput(@Size(min = 1, max = 8) List<@Valid RoundInput> rounds) {
    }

    public record MeasuresInput(@Size(max = 15) List<String> measures) {
    }

    private final CompanySettingsService settings;
    private final CentreAdminService centres;

    public AdminSettingsController(CompanySettingsService settings, CentreAdminService centres) {
        this.settings = settings;
        this.centres = centres;
    }

    @GetMapping("/company")
    public CompanySettingsService.CompanyDetails company() {
        return settings.company();
    }

    @PutMapping("/company")
    @AdminOnly
    public CompanySettingsService.CompanyDetails saveCompany(@Valid @RequestBody CompanySettingsService.CompanyInput body) {
        return settings.saveCompany(body);
    }

    @GetMapping("/round-plan")
    public List<JobDtos.RoundView> roundPlan() {
        return settings.roundPlan();
    }

    @PutMapping("/round-plan")
    @AdminOnly
    public List<JobDtos.RoundView> saveRoundPlan(@Valid @RequestBody RoundPlanInput body) {
        return settings.saveRoundPlan(body.rounds());
    }

    @GetMapping("/review-measures")
    public List<String> measures() {
        return settings.measures();
    }

    @PutMapping("/review-measures")
    @AdminOnly
    public List<String> saveMeasures(@Valid @RequestBody MeasuresInput body) {
        return settings.saveMeasures(body.measures());
    }

    public record ReasonsInput(@Size(max = 20) List<@Size(max = 300) String> reasons) {
    }

    @GetMapping("/decision-reasons")
    public List<String> decisionReasons() {
        return settings.decisionReasons();
    }

    @PutMapping("/decision-reasons")
    @AdminOnly
    public List<String> saveDecisionReasons(@Valid @RequestBody ReasonsInput body) {
        return settings.saveDecisionReasons(body.reasons());
    }

    @GetMapping("/email-templates")
    public List<CompanySettingsService.EmailTemplateView> emailTemplates() {
        return settings.emailTemplates();
    }

    @PutMapping("/email-templates/{key}")
    @AdminOnly
    public CompanySettingsService.EmailTemplateView saveEmailTemplate(@PathVariable String key,
                                                                      @Valid @RequestBody CompanySettingsService.EmailTemplateInput body) {
        return settings.saveEmailTemplate(key, body);
    }

    @DeleteMapping("/email-templates/{key}")
    @AdminOnly
    public CompanySettingsService.EmailTemplateView resetEmailTemplate(@PathVariable String key) {
        return settings.resetEmailTemplate(key);
    }

    @PostMapping("/email-templates/{key}/preview")
    public CompanySettingsService.Preview preview(@PathVariable String key,
                                                  @Valid @RequestBody CompanySettingsService.EmailTemplateInput body) {
        return settings.preview(key, body);
    }

    @GetMapping("/centres")
    public List<CentreAdminService.CentreView> centres() {
        return centres.list();
    }

    @PostMapping("/centres")
    @AdminOnly
    public CentreAdminService.CentreView createCentre(@Valid @RequestBody CentreAdminService.CentreInput body) {
        return centres.create(body);
    }

    @PutMapping("/centres/{id}")
    @AdminOnly
    public CentreAdminService.CentreView updateCentre(@PathVariable UUID id, @Valid @RequestBody CentreAdminService.CentreInput body) {
        return centres.update(id, body);
    }

    @PostMapping("/centres/{id}/assessors")
    @AdminOnly
    public CentreAdminService.CentreView addAssessor(@PathVariable UUID id, @Valid @RequestBody CentreAdminService.AssessorInput body) {
        return centres.addAssessor(id, body);
    }

    @PutMapping("/centres/{id}/assessors/{assessorId}")
    @AdminOnly
    public CentreAdminService.CentreView updateAssessor(@PathVariable UUID id, @PathVariable UUID assessorId,
                                                        @Valid @RequestBody CentreAdminService.AssessorInput body) {
        return centres.updateAssessor(id, assessorId, body);
    }

    @GetMapping("/centres/{id}/slots")
    public List<CentreAdminService.SlotView> slots(@PathVariable UUID id) {
        return centres.slots(id);
    }

    @PostMapping("/centres/{id}/slots")
    @AdminOnly
    public CentreAdminService.SlotResult addSlots(@PathVariable UUID id, @Valid @RequestBody CentreAdminService.SlotPattern body) {
        return centres.addSlots(id, body);
    }

    @DeleteMapping("/centres/{id}/slots/{slotId}")
    @AdminOnly
    public ResponseEntity<Void> removeSlot(@PathVariable UUID id, @PathVariable UUID slotId) {
        centres.removeSlot(id, slotId);
        return ResponseEntity.noContent().build();
    }
}
