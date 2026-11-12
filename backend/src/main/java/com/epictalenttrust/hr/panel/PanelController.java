package com.epictalenttrust.hr.panel;

import com.epictalenttrust.hr.activity.ProcessingLog;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.panel.PanelDtos.AccessInput;
import com.epictalenttrust.hr.panel.PanelDtos.ReviewInput;
import com.epictalenttrust.hr.panel.PanelDtos.ReviewPage;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.storage.FileStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The generic panel login's only pages: enter employee ID and interview code, then the one review that opens. The
 * open review is kept in the session; entering another code replaces it.
 */
@RestController
@RequestMapping("/api/panel")
public class PanelController {

    static final String SESSION_KEY = "panel.memberId";

    private final PanelReviewService reviews;
    private final FileStorage storage;
    private final ProcessingLog processingLog;
    private final JdbcClient jdbc;

    public PanelController(PanelReviewService reviews, FileStorage storage, ProcessingLog processingLog, JdbcClient jdbc) {
        this.reviews = reviews;
        this.storage = storage;
        this.processingLog = processingLog;
        this.jdbc = jdbc;
    }

    @PostMapping("/access")
    public ReviewPage access(@Valid @RequestBody AccessInput body, HttpServletRequest request, HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        UUID memberId = reviews.open(body.employeeId(), body.code(), request.getRemoteAddr());
        session.setAttribute(SESSION_KEY, memberId);
        return reviews.page(memberId);
    }

    @GetMapping("/review")
    public ReviewPage review(HttpSession session) {
        return reviews.page(memberId(session));
    }

    /** Saves a draft as the reviewer types. */
    @PutMapping("/review")
    public ReviewPage save(@Valid @RequestBody ReviewInput body, HttpServletRequest request, HttpSession session) {
        return reviews.save(memberId(session), body, false, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/review/submit")
    public ReviewPage submit(@Valid @RequestBody ReviewInput body, HttpServletRequest request, HttpSession session) {
        return reviews.save(memberId(session), body, true, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    /** Back to the employee ID and code page. */
    @PostMapping("/exit")
    public ResponseEntity<Void> exit(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return ResponseEntity.noContent().build();
    }

    /** The candidate's CV, for the panel member whose review is open. */
    @GetMapping("/review/cv")
    public ResponseEntity<byte[]> cv(HttpServletRequest request, HttpSession session) {
        ReviewPage page = reviews.page(memberId(session));
        record Cv(UUID applicationId, String key, String name) {
        }
        Cv cv = jdbc.sql("""
                        select a.id, a.cv_file_key, c.full_name from interviews i join applications a on a.id = i.application_id
                          join candidates c on c.id = a.candidate_id where i.id = :i
                        """)
                .param("i", page.interviewId())
                .query((rs, n) -> new Cv(rs.getObject("id", UUID.class), rs.getString("cv_file_key"), rs.getString("full_name")))
                .single();
        if (cv.key() == null) {
            throw ApiException.notFound("CV file");
        }
        byte[] content = storage.get(cv.key());
        processingLog.documentAccess(cv.applicationId(), cv.key(), ProcessingLog.DocAction.VIEW, "hr_user", CurrentUser.id(),
                request.getRemoteAddr());
        String extension = cv.key().substring(cv.key().lastIndexOf('.') + 1);
        String fileName = cv.name().replaceAll("[^A-Za-z0-9 ]", "").replace(' ', '_') + "_CV." + extension;
        return ResponseEntity.ok()
                .contentType("pdf".equals(extension) ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ("pdf".equals(extension) ? ContentDisposition.inline() : ContentDisposition.attachment())
                                .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }

    private static UUID memberId(HttpSession session) {
        if (!(session.getAttribute(SESSION_KEY) instanceof UUID id)) {
            throw ApiException.notFound("Review");
        }
        return id;
    }
}
