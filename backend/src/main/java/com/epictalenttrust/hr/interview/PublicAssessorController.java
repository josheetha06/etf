package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.interview.AssessorLinkService.AssessorReport;
import com.epictalenttrust.hr.interview.AssessorLinkService.LinkView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The partner assessor's one-time link (no sign-in). The token in the link is the only credential; it is stored
 * hashed, expires two weeks after the interview and stops working once the report is sent.
 */
@RestController
public class PublicAssessorController {

    private final AssessorLinkService links;

    public PublicAssessorController(AssessorLinkService links) {
        this.links = links;
    }

    @GetMapping("/api/public/assessor/{token}")
    public LinkView view(@PathVariable String token) {
        return links.view(token);
    }

    @PostMapping("/api/public/assessor/{token}")
    public LinkView submit(@PathVariable String token, @RequestBody AssessorReport report) {
        links.submit(token, report);
        return links.view(token);
    }
}
