package com.geofields.repository;

import java.util.Optional;

public interface OrganizationRepository {

    Optional<Long> findIdByName(String name);

    Optional<String> findNameById(long organizationId);
}
