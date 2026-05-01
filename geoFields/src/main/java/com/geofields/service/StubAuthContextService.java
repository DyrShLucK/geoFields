package com.geofields.service;

import org.springframework.stereotype.Service;

// Временная заглушка авторизации: всегда "залогинен" один и тот же пользователь.
@Service
public class StubAuthContextService {

    public Long getCurrentUserId() {
        return 1L;
    }

    public Long getCurrentOrganizationId() {
        return 1L;
    }
}
