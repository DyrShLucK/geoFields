package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.repository.FieldHistoryRow;
import com.geofields.repository.FieldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

// Чтение из БД + сборка GeoJSON. Кэш отключён (проблемы с Redis/Jackson); при возврате кэша:
// @EnableCaching, spring.cache.type=redis, @Cacheable(cacheNames="fieldFeaturesByOrg", key="#organizationId")
// и RedisCacheManager с ObjectMapper как у HTTP — см. удалённый GeoFieldsRedisCacheConfiguration в истории.
@Service
public class FieldGeoJsonQueryService {

    private static final Logger log = LoggerFactory.getLogger(FieldGeoJsonQueryService.class);

    private final FieldRepository fieldRepository;
    private final FieldGeoJsonAssembler assembler;

    public FieldGeoJsonQueryService(FieldRepository fieldRepository, FieldGeoJsonAssembler assembler) {
        this.fieldRepository = fieldRepository;
        this.assembler = assembler;
    }

    public FieldFeatureCollectionDto loadForOrganization(Long organizationId) {
        List<FieldHistoryRow> rows = fieldRepository.findAllFieldsWithHistory(organizationId);
        log.info("Repository returned {} joined rows for /get_fields, org={}", rows.size(), organizationId);
        FieldFeatureCollectionDto dto = assembler.toFeatureCollection(rows);
        log.info("Built {} GeoJSON features for /get_fields", dto.features().size());
        return dto;
    }
}
