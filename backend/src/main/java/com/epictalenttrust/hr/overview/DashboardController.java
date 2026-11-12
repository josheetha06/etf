package com.epictalenttrust.hr.overview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/dashboard")
    public DashboardService.Dashboard dashboard(@RequestParam(required = false) String zone) {
        return dashboard.dashboard(zone);
    }
}
