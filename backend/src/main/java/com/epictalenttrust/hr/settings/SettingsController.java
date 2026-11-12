package com.epictalenttrust.hr.settings;

import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.security.AdminOnly;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.settings.BrandingService.Branding;
import com.epictalenttrust.hr.settings.BrandingService.Logo;
import com.epictalenttrust.hr.settings.ScreeningSettingsService.Accuracy;
import com.epictalenttrust.hr.settings.ScreeningSettingsService.SkillInput;
import com.epictalenttrust.hr.settings.ScreeningSettingsService.SkillView;
import com.epictalenttrust.hr.settings.ScreeningSettingsService.Thresholds;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class SettingsController {

    private final BrandingService branding;
    private final ScreeningSettingsService screening;

    public SettingsController(BrandingService branding, ScreeningSettingsService screening) {
        this.branding = branding;
        this.screening = screening;
    }

    // ------------------------------------------------------------------ branding

    @GetMapping("/api/branding")
    public Branding branding() {
        return branding.branding(CurrentUser.companyId());
    }

    @GetMapping("/api/branding/logo")
    public ResponseEntity<byte[]> logo() {
        return image(branding.logo(CurrentUser.companyId()));
    }

    /** For the sign-in page, when this installation serves one client. */
    @GetMapping("/api/public/branding")
    public Branding publicBranding() {
        return branding.soleCompany().map(branding::branding).orElse(new Branding(null, false, null, false));
    }

    @GetMapping("/api/public/branding/logo")
    public ResponseEntity<byte[]> publicLogo() {
        return image(branding.soleCompany().flatMap(branding::logo));
    }

    @PutMapping(value = "/api/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminOnly
    public Branding uploadLogo(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.getSize() > LogoImage.MAX_BYTES) {
            throw ApiException.badRequest("logo_too_big", "The logo is larger than 1 MB. Please use a smaller image.");
        }
        return branding.saveLogo(file.getBytes(), file.getOriginalFilename());
    }

    @DeleteMapping("/api/settings/logo")
    @AdminOnly
    public Branding removeLogo() {
        return branding.removeLogo();
    }

    private static ResponseEntity<byte[]> image(Optional<Logo> logo) {
        return logo.map(l -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(l.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .eTag(Long.toString(l.updatedAt() == null ? 0 : l.updatedAt().toEpochMilli()))
                        .header("X-Content-Type-Options", "nosniff")
                        .body(l.image()))
                .orElse(ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------ shortlisting

    @GetMapping("/api/settings/screening")
    public Thresholds thresholds() {
        return screening.thresholds();
    }

    @PutMapping("/api/settings/screening")
    @AdminOnly
    public Thresholds saveThresholds(@Valid @RequestBody Thresholds body) {
        return screening.saveThresholds(body);
    }

    @GetMapping("/api/settings/skills")
    public List<SkillView> skills(@RequestParam(required = false) String q) {
        return screening.skills(q);
    }

    @PostMapping("/api/settings/skills")
    @AdminOnly
    public SkillView addSkill(@Valid @RequestBody SkillInput body) {
        return screening.addSkill(body);
    }

    @PutMapping("/api/settings/skills/{id}")
    @AdminOnly
    public SkillView updateSkill(@PathVariable UUID id, @Valid @RequestBody SkillInput body) {
        return screening.updateSkill(id, body);
    }

    @DeleteMapping("/api/settings/skills/{id}")
    @AdminOnly
    public ResponseEntity<Void> deleteSkill(@PathVariable UUID id) {
        screening.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/settings/accuracy")
    public Accuracy accuracy(@RequestParam(required = false) UUID jobId) {
        return screening.accuracy(jobId);
    }
}
