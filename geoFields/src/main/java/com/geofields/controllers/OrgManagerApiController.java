package com.geofields.controllers;

import com.geofields.dto.orgmanager.ActionResult;
import com.geofields.dto.orgmanager.CsrfInfo;
import com.geofields.dto.orgmanager.InviteCreated;
import com.geofields.dto.orgmanager.InviteItem;
import com.geofields.dto.orgmanager.PendingItem;
import com.geofields.dto.orgmanager.SummaryResponse;
import com.geofields.dto.orgmanager.TokenRequest;
import com.geofields.dto.orgmanager.UserIdRequest;
import com.geofields.repository.OrgRegistrationInviteRepository;
import com.geofields.repository.OrganizationRepository;
import com.geofields.repository.UserAccountRepository;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.service.OrgRegistrationInviteTokenService;
import com.geofields.support.web.CsrfTokenReader;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org/manager")
public class OrgManagerApiController {
    private static final Logger log = LoggerFactory.getLogger(OrgManagerApiController.class);

    private final UserAccountRepository userAccountRepository;
    private final OrgRegistrationInviteRepository inviteRepository;
    private final OrgRegistrationInviteTokenService inviteTokenService;
    private final OrganizationRepository organizationRepository;

    public OrgManagerApiController(
            UserAccountRepository userAccountRepository,
            OrgRegistrationInviteRepository inviteRepository,
            OrgRegistrationInviteTokenService inviteTokenService,
            OrganizationRepository organizationRepository) {
        this.userAccountRepository = userAccountRepository;
        this.inviteRepository = inviteRepository;
        this.inviteTokenService = inviteTokenService;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> summary(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            HttpServletRequest request) {
        long orgId = user.getOrganizationId();
        List<PendingItem> pending = userAccountRepository.findPendingRegistrations(orgId).stream()
                .map(p -> new PendingItem(
                        p.id(),
                        nullToEmpty(p.lastName()),
                        nullToEmpty(p.firstName()),
                        nullToEmpty(p.middleName()),
                        p.login(),
                        p.email(),
                        p.createdAt()))
                .toList();
        List<InviteItem> invites = inviteRepository.findActiveTokensByOrganization(orgId).stream()
                .map(t -> new InviteItem(t, inviteTokenService.registerRelativeUrl(t)))
                .toList();
        CsrfInfo csrf = CsrfTokenReader.read(request);
        String orgName = organizationRepository.findNameById(orgId).orElse("");
        SummaryResponse body = new SummaryResponse(
                orgId,
                orgName,
                user.getUsername(),
                user.getFullName(),
                pending,
                invites,
                csrf);
        return ResponseEntity.ok(body);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    @PostMapping("/approve")
    public ResponseEntity<ActionResult> approve(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestBody UserIdRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        long targetUserId = body.userId();
        int n = userAccountRepository.approveUser(targetUserId, orgId);
        String msg = n == 1 ? "Пользователь принят." : "Заявка не найдена или уже обработана.";
        log.info("userId={} orgId={} action=approve_registration targetUserId={} success={}",
                actorUserId, orgId, targetUserId, n == 1);
        return ResponseEntity.ok(new ActionResult(n == 1, msg));
    }

    @PostMapping("/reject")
    public ResponseEntity<ActionResult> reject(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestBody UserIdRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        long targetUserId = body.userId();
        int n = userAccountRepository.rejectUser(targetUserId, orgId);
        String msg = n == 1 ? "Заявка отклонена." : "Заявка не найдена или уже обработана.";
        log.info("userId={} orgId={} action=reject_registration targetUserId={} success={}",
                actorUserId, orgId, targetUserId, n == 1);
        return ResponseEntity.ok(new ActionResult(n == 1, msg));
    }

    @PostMapping("/invite")
    public ResponseEntity<InviteCreated> createInvite(@AuthenticationPrincipal GeoFieldsUserDetails user) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        String token = inviteTokenService.newToken();
        inviteRepository.insertInvite(orgId, token, actorUserId);
        String url = inviteTokenService.registerRelativeUrl(token);
        log.info("userId={} orgId={} action=create_invite success=true", actorUserId, orgId);
        return ResponseEntity.ok(new InviteCreated(token, url, "Новая одноразовая ссылка создана."));
    }

    @PostMapping("/revoke-invite")
    public ResponseEntity<ActionResult> revokeInvite(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestBody TokenRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        String trimmedToken = body.token().trim();
        inviteRepository.revokeToken(trimmedToken, orgId);
        log.info("userId={} orgId={} action=revoke_invite success=true", actorUserId, orgId);
        return ResponseEntity.ok(new ActionResult(true, "Приглашение отозвано."));
    }
}
