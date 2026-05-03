package com.geofields.service;

import com.geofields.security.GeoFieldsUserDetails;

import java.util.Optional;

// Контекст «кто сейчас в системе» — отдельный контракт, чтобы не тянуть Security в сервисы напрямую.
public interface AuthContextService {

    Optional<GeoFieldsUserDetails> currentUser();

    boolean isAdmin();

    boolean isOrgManager();

    Long getCurrentOrganizationId();

    Long getCurrentUserId();

    String getCurrentLogin();
}
