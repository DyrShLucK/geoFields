package com.geofields.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Страница аналитики и отчётов — {@code templates/org-reports.html}. */
@Controller
public class ReportsPageController {

    @GetMapping("/org/reports")
    public String reportsPage() {
        return "org-reports";
    }
}
