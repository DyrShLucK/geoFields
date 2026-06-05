package com.geofields.repository;

import java.util.List;
import java.util.Optional;

/** Вспомогательные геозапросы для legacy NDVI-эндпоинтов (без чтения field_analytics). */
public interface NdviAnalyticsRepository {

    List<Long> findDistinctFieldIdsForOrganization(long organizationId);

    /**
     * Поле организации, в геометрию которого попадает точка (WGS84). Требуется PostGIS.
     */
    Optional<Long> findFieldIdCoveringPoint(long organizationId, double latitude, double longitude);
}
