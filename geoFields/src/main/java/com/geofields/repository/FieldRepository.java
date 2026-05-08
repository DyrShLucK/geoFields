package com.geofields.repository;

import com.geofields.repository.row.FieldHistoryRow;

import java.util.List;

// Контракт репозитория для работы с таблицей fields.
public interface FieldRepository {
    List<FieldHistoryRow> findAllFieldsWithHistory(Long organizationId);

    boolean fieldBelongsToOrganization(Long fieldId, Long organizationId);
}
