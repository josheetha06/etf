package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.report.ReportCenter.Counts;
import com.epictalenttrust.hr.report.ReportCenter.ReportRow;
import com.epictalenttrust.hr.security.CanManageHiring;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
public class ReportController {

    public record RecheckInput(@Size(max = 1000) String note) {
    }

    private final ReportCenter reports;

    public ReportController(ReportCenter reports) {
        this.reports = reports;
    }

    @GetMapping("/api/reports")
    public List<ReportRow> list(@RequestParam(defaultValue = "to_review") String view,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) UUID jobId,
                                @RequestParam(defaultValue = "false") boolean flagged) {
        return reports.list(ReportCenter.View.valueOf(view.toUpperCase(Locale.ROOT)), type, jobId, flagged);
    }

    @GetMapping("/api/reports/counts")
    public Counts counts() {
        return reports.counts();
    }

    @PostMapping("/api/applications/{applicationId}/ai-recheck")
    @CanManageHiring
    public ResponseEntity<Void> recheck(@PathVariable UUID applicationId, @RequestBody RecheckInput input) {
        reports.requestAiRecheck(applicationId, input.note());
        return ResponseEntity.noContent().build();
    }
}
