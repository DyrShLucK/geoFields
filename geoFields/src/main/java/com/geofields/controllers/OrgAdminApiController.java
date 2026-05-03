package com.geofields.controllers;

import com.geofields.dto.orgadmin.AdminSummaryResponse;
import com.geofields.dto.orgadmin.OrgMemberItem;
import com.geofields.dto.orgadmin.RoleUpdateRequest;
import com.geofields.dto.orgmanager.ActionResult;
import com.geofields.dto.orgmanager.UserIdRequest;
import com.geofields.repository.OrganizationMemberRow;
import com.geofields.repository.OrganizationRepository;
import com.geofields.repository.UserAccountRepository;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import com.geofields.service.AdminOrganizationUserService;
import com.geofields.support.web.CsrfTokenReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org/admin")
public class OrgAdminApiController {

    private final UserAccountRepository userAccountRepository;
    private final AdminOrganizationUserService adminOrganizationUserService;
    private final OrganizationRepository organizationRepository;

    public OrgAdminApiController(
            UserAccountRepository userAccountRepository,
            AdminOrganizationUserService adminOrganizationUserService,
            OrganizationRepository organizationRepository) {
        this.userAccountRepository = userAccountRepository;
        this.adminOrganizationUserService = adminOrganizationUserService;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminSummaryResponse> summary(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            HttpServletRequest request) {
        long orgId = user.getOrganizationId();
        List<OrgMemberItem> members = userAccountRepository.findMembersByOrganization(orgId).stream()
                .map(row -> toItem(row, user.getUserId()))
                .toList();
        String orgName = organizationRepository.findNameById(orgId).orElse("");
        AdminSummaryResponse body = new AdminSummaryResponse(
                orgId,
                orgName,
                user.getUsername(),
                user.getFullName(),
                members,
                CsrfTokenReader.read(request));
        return ResponseEntity.ok(body);
    }

    private static OrgMemberItem toItem(OrganizationMemberRow row, long currentUserId) {
        UserRole r = row.role();
        return new OrgMemberItem(
                row.id(),
                row.lastName(),
                row.firstName(),
                row.middleName(),
                row.login(),
                row.email(),
                r.name(),
                r.displayNameRu(),
                row.registrationStatus(),
                row.active(),
                row.id() == currentUserId);
    }

    @PostMapping("/role")
    public ResponseEntity<ActionResult> updateRole(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestBody RoleUpdateRequest body) {
        UserRole newRole;
        try {
            newRole = UserRole.valueOf(body.role() != null ? body.role().trim().toUpperCase() : "");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(new ActionResult(false, "Неизвестная роль."));
        }
        ActionResult result = adminOrganizationUserService.updateMemberRole(user, body.userId(), newRole);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/delete")
    public ResponseEntity<ActionResult> deleteUser(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestBody UserIdRequest body) {
        ActionResult result = adminOrganizationUserService.deleteMember(user, body.userId());
        return ResponseEntity.ok(result);
    }
}
