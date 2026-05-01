package com.geofields.repository;

import com.geofields.model.Fields;

import java.util.List;

// Контракт репозитория для работы с таблицей fields.
public interface FieldRepository {
    List<Fields> findAllFields();
}
