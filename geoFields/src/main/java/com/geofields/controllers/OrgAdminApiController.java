package com.geofields.controllers;

import com.geofields.dto.orgadmin.AdminSummaryResponse;
import com.geofields.dto.orgadmin.OrgMemberItem;
import com.geofields.dto.orgadmin.RoleUpdateRequest;
import com.geofields.dto.orgmanager.ActionResult;
import com.geofields.dto.orgmanager.UserIdRequest;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.OrganizationRepository;
import com.geofields.repository.UserAccountRepository;
import com.geofields.repository.row.OrganizationMemberRow;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import com.geofields.service.AdminOrganizationUserService;
import com.geofields.support.web.CsrfTokenReader;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org/admin")
public class OrgAdminApiController {
    private static final Logger log = LoggerFactory.getLogger(OrgAdminApiController.class);

    private final UserAccountRepository userAccountRepository;
    private final AdminOrganizationUserService adminOrganizationUserService;
    private final OrganizationRepository organizationRepository;
    private final FieldRepository fieldRepository;

    public OrgAdminApiController(
            UserAccountRepository userAccountRepository,
            AdminOrganizationUserService adminOrganizationUserService,
            OrganizationRepository organizationRepository,
            FieldRepository fieldRepository) {
        this.userAccountRepository = userAccountRepository;
        this.adminOrganizationUserService = adminOrganizationUserService;
        this.organizationRepository = organizationRepository;
        this.fieldRepository = fieldRepository;
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
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        UserRole newRole;
        try {
            newRole = UserRole.valueOf(body.role() != null ? body.role().trim().toUpperCase() : "");
        } catch (IllegalArgumentException ex) {
            log.warn("userId={} orgId={} action=update_role targetUserId={} requestedRole={} success=false reason=invalid_role",
                    actorUserId, orgId, body.userId(), body.role());
            return ResponseEntity.ok(new ActionResult(false, "Неизвестная роль."));
        }
        ActionResult result = adminOrganizationUserService.updateMemberRole(user, body.userId(), newRole);
        log.info("userId={} orgId={} action=update_role targetUserId={} role={} success={}",
                actorUserId, orgId, body.userId(), newRole, result.ok());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/delete")
    public ResponseEntity<ActionResult> deleteUser(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestBody UserIdRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        ActionResult result = adminOrganizationUserService.deleteMember(user, body.userId());
        log.info("userId={} orgId={} action=delete_user targetUserId={} success={}",
                actorUserId, orgId, body.userId(), result.ok());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/fields/{fieldId}")
    public ResponseEntity<ActionResult> deleteField(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long fieldId) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            log.warn("userId={} orgId={} action=delete_field fieldId={} success=false reason=not_found_or_foreign",
                    actorUserId, orgId, fieldId);
            return ResponseEntity.notFound().build();
        }
        int deleted = fieldRepository.deleteField(fieldId, orgId);
        if (deleted == 0) {
            log.warn("userId={} orgId={} action=delete_field fieldId={} success=false reason=no_rows_deleted",
                    actorUserId, orgId, fieldId);
            return ResponseEntity.notFound().build();
        }
        log.info("userId={} orgId={} action=delete_field fieldId={} success=true",
                actorUserId, orgId, fieldId);
        return ResponseEntity.ok(new ActionResult(true, "Поле удалено."));
    }
}
