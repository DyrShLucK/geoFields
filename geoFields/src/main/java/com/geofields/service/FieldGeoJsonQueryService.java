package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.repository.FieldRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class FieldGeoJsonQueryService {

    private final FieldRepository fieldRepository;
    private final FieldGeoJsonAssembler assembler;

    public FieldGeoJsonQueryService(FieldRepository fieldRepository, FieldGeoJsonAssembler assembler) {
        this.fieldRepository = fieldRepository;
        this.assembler = assembler;
    }

    public FieldFeatureCollectionDto loadForOrganization(Long organizationId) {
        return assembler.toFeatureCollection(fieldRepository.findAllFieldsWithHistory(organizationId));
    }

    public FieldFeatureCollectionDto loadIntersectingForField(Long organizationId, Long fieldId) {
        return assembler.toFeatureCollection(fieldRepository.findIntersectingFieldsWithHistory(organizationId, fieldId));
    }

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
