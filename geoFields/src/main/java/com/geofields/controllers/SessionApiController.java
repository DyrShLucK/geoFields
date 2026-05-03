package com.geofields.controllers;

import com.geofields.dto.session.SessionContextResponse;
import com.geofields.repository.OrganizationRepository;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.support.web.CsrfTokenReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionApiController {

    private final OrganizationRepository organizationRepository;

    public SessionApiController(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/context")
    public ResponseEntity<SessionContextResponse> context(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            HttpServletRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        long orgId = user.getOrganizationId();
        String orgName = organizationRepository.findNameById(orgId).orElse("");
        SessionContextResponse body = new SessionContextResponse(
                user.getUserId(),
                orgId,
                orgName,
                user.getUsername(),
                user.getFullName(),
                user.getLastName(),
                user.getFirstName(),
                user.getMiddleName(),
                user.isAdmin(),
                user.isOrgManager(),
                user.isAgronomist(),
                user.getRole().name(),
                user.getRole().displayNameRu(),
                CsrfTokenReader.read(request));
        return ResponseEntity.ok(body);
    }
}
