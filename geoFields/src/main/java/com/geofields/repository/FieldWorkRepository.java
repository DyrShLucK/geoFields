package com.geofields.repository;

import com.geofields.repository.row.FieldWorkCodeRow;
import com.geofields.repository.row.FieldWorkItemRow;
import com.geofields.repository.row.FieldWorkStatusHistoryRow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FieldWorkRepository {

    List<FieldWorkCodeRow> listStatuses();

    List<FieldWorkCodeRow> listCategories();

    boolean statusExists(String code);

    boolean categoryExists(String code);

    boolean operationBelongsToOrganization(long operationId, long organizationId);

    List<FieldWorkItemRow> listByField(long fieldId, long organizationId);

    Optional<FieldWorkItemRow> findById(long operationId, long organizationId);

    List<FieldWorkStatusHistoryRow> listStatusHistory(long operationId);

    long insertOperation(
            long fieldId,
            long organizationId,
            long userId,
            String name,
            String category,
            String status,
            LocalDateTime operationAt);

    void insertStatusHistory(long operationId, String status, long userId, String note);

    int updateOperation(
            long operationId,
            long organizationId,
            String name,
            String category,
            String status,
            LocalDateTime operationAt);

    int deleteOperation(long operationId, long organizationId);
}
