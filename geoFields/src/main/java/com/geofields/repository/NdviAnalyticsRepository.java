package com.geofields.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** NDVI в разрезе field_analytics + ndvi_data (дата = record_date). */
public interface NdviAnalyticsRepository {

    Optional<String> findNdviTileUrlForFieldOnDate(long fieldId, long organizationId, LocalDate recordDate);

    List<Long> findDistinctFieldIdsForOrganization(long organizationId);

    /**
     * Поле организации, в геометрию которого попадает точка (WGS84). Требуется PostGIS.
     */
    Optional<Long> findFieldIdCoveringPoint(long organizationId, double latitude, double longitude);
}
