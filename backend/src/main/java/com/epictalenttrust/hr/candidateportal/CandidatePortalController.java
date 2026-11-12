package com.epictalenttrust.hr.candidateportal;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The candidate's own page. Public: the secure link in their email is the key. */
@RestController
@RequestMapping("/api/public/candidate/{token}")
public class CandidatePortalController {

    private final CandidatePortalService portal;

    public CandidatePortalController(CandidatePortalService portal) {
        this.portal = portal;
    }

    @GetMapping
    public CandidatePortalService.Page page(@PathVariable String token) {
        return portal.page(token);
    }

    @PostMapping("/accept")
    public CandidatePortalService.Page accept(@PathVariable String token) {
        return portal.accept(token);
    }

    @PostMapping("/new-time")
    public CandidatePortalService.Page newTime(@PathVariable String token,
                                               @Valid @RequestBody CandidatePortalService.NewTimeInput body) {
        return portal.askForNewTime(token, body);
    }

    @PostMapping("/messages")
    public CandidatePortalService.Page message(@PathVariable String token,
                                               @Valid @RequestBody CandidatePortalService.MessageInput body) {
        return portal.message(token, body);
    }
}
