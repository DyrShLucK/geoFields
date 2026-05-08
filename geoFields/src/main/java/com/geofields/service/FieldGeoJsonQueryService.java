package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.row.FieldHistoryRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Читает данные полей и собирает GeoJSON-ответы.
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

    /** Возвращает подмножество Feature по списку field id. */
    public FieldFeatureCollectionDto loadFeatureSubsetByFieldIds(Long organizationId, Collection<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return new FieldFeatureCollectionDto("FeatureCollection", List.of());
        }
        Set<Long> wanted = fieldIds instanceof Set<Long> set
                ? set
                : new LinkedHashSet<>(fieldIds);
        FieldFeatureCollectionDto all = loadForOrganization(organizationId);
        List<FieldFeatureDto> filtered = all.features().stream()
                .filter(f -> wanted.contains(f.id()))
                .toList();
        return new FieldFeatureCollectionDto("FeatureCollection", filtered);
    }
}
