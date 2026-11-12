package com.epictalenttrust.hr.audit;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** The Audit trail page, the History section on job, candidate and interview pages, and the CSV download. */
@RestController
public class AuditTrailController {

    private final AuditTrailService audit;

    public AuditTrailController(AuditTrailService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/audit")
    public AuditTrailService.Page list(@RequestParam(required = false) String type,
                                       @RequestParam(required = false) String person,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) UUID jobId,
                                       @RequestParam(required = false) UUID applicationId,
                                       @RequestParam(required = false) UUID interviewId,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Long before,
                                       @RequestParam(defaultValue = "50") int size) {
        return audit.list(new AuditTrailService.Filter(type, person, from, to, jobId, applicationId, interviewId, q, before, size));
    }

    @GetMapping("/api/audit.csv")
    public ResponseEntity<byte[]> csv(@RequestParam(required = false) String type,
                                      @RequestParam(required = false) String person,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) UUID jobId,
                                      @RequestParam(required = false) String q) {
        String body = audit.csv(new AuditTrailService.Filter(type, person, from, to, jobId, null, null, q, null, 200));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("audit-trail.csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }
}
