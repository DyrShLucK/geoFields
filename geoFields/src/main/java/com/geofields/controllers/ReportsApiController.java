package com.geofields.controllers;

import com.geofields.dto.ErrorResponseDto;
import com.geofields.dto.reports.ReportsDashboardResponse;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.service.ReportsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportsApiController {

    private final ReportsService reportsService;

    public ReportsApiController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    /**
     * Сводный дашборд для отчёта: KPI, графики операций и посевов, таблица по полям.
     *
     * @param fieldIds   id полей через запятую; пусто — все поля организации
     * @param dateFrom   начало периода (включительно)
     * @param dateTo     конец периода (включительно)
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestParam(required = false) String fieldIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        try {
            ReportsDashboardResponse body = reportsService.buildDashboard(
                    user.getOrganizationId(),
                    fieldIds,
                    dateFrom,
                    dateTo);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(ex.getMessage()));
        }
    }
}
