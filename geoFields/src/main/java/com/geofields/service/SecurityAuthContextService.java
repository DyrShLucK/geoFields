package com.geofields.service;

import com.geofields.security.GeoFieldsUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SecurityAuthContextService implements AuthContextService {

    @Override
    public Optional<GeoFieldsUserDetails> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof GeoFieldsUserDetails details)) {
            return Optional.empty();
        }
        return Optional.of(details);
    }

    @Override
    public boolean isAdmin() {
        return currentUser().map(GeoFieldsUserDetails::isAdmin).orElse(false);
    }

    @Override
    public boolean isOrgManager() {
        return currentUser().map(GeoFieldsUserDetails::isOrgManager).orElse(false);
    }

    @Override
    public Long getCurrentOrganizationId() {
        return currentUser().map(GeoFieldsUserDetails::getOrganizationId).orElse(null);
    }

    @Override
    public Long getCurrentUserId() {
        return currentUser().map(GeoFieldsUserDetails::getUserId).orElse(null);
    }

    @Override
    public String getCurrentLogin() {
        return currentUser().map(GeoFieldsUserDetails::getUsername).orElse(null);
    }
}
