package com.geofields.repository;

import com.geofields.repository.row.FieldWorkCodeRow;
import com.geofields.repository.row.FieldWorkItemRow;
import com.geofields.repository.row.FieldWorkStatusHistoryRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFieldWorkRepository implements FieldWorkRepository {

    private static final String LIST_STATUSES = """
            SELECT code, title_ru FROM field_operation_status ORDER BY sort_order
            """;

    private static final String LIST_CATEGORIES = """
            SELECT code, title_ru FROM field_operation_category ORDER BY sort_order
            """;

    private static final String STATUS_EXISTS = """
            SELECT EXISTS (SELECT 1 FROM field_operation_status WHERE code = ?)
            """;

    private static final String CATEGORY_EXISTS = """
            SELECT EXISTS (SELECT 1 FROM field_operation_category WHERE code = ?)
            """;

    private static final String BELONGS_TO_ORG = """
            SELECT EXISTS (
                SELECT 1 FROM field_operations
                WHERE id = ? AND organization_id = ?
            )
            """;

    private static final String SELECT_ITEM_COLUMNS = """
            SELECT fo.id,
                   fo.field_id,
                   fo.organization_id,
                   fo.name,
                   fo.category,
                   fc.title_ru AS category_title_ru,
                   fo.status,
                   fs.title_ru AS status_title_ru,
                   fo.operation_at,
                   fo.user_id,
                   fo.created_at,
                   fo.updated_at
            FROM field_operations fo
            JOIN field_operation_category fc ON fc.code = fo.category
            JOIN field_operation_status fs ON fs.code = fo.status
            """;

    private static final String LIST_BY_FIELD = SELECT_ITEM_COLUMNS + """
            WHERE fo.field_id = ? AND fo.organization_id = ?
            ORDER BY fo.operation_at DESC, fo.id DESC
            """;

    private static final String FIND_BY_ID = SELECT_ITEM_COLUMNS + """
            WHERE fo.id = ? AND fo.organization_id = ?
            """;

    private static final String LIST_STATUS_HISTORY = """
            SELECT h.id,
                   h.operation_id,
                   h.status,
                   st.title_ru AS status_title_ru,
                   h.changed_at,
                   h.user_id,
                   u.last_name,
                   u.first_name,
                   h.note
            FROM field_operation_status_history h
            JOIN field_operation_status st ON st.code = h.status
            LEFT JOIN users u ON u.id = h.user_id
            WHERE h.operation_id = ?
            ORDER BY h.changed_at ASC, h.id ASC
            """;

    private static final String INSERT_OPERATION = """
            INSERT INTO field_operations (
                field_id, organization_id, user_id, operation_at, name, category, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_STATUS_HISTORY = """
            INSERT INTO field_operation_status_history (operation_id, status, user_id, note)
            VALUES (?, ?, ?, ?)
            """;

    private static final String UPDATE_OPERATION = """
            UPDATE field_operations
            SET name = ?,
                category = ?,
                status = ?,
                operation_at = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND organization_id = ?
            """;

    private static final String DELETE_OPERATION = """
            DELETE FROM field_operations WHERE id = ? AND organization_id = ?
            """;

    private static final RowMapper<FieldWorkCodeRow> CODE_ROW_MAPPER =
            (rs, n) -> new FieldWorkCodeRow(rs.getString("code"), rs.getString("title_ru"));

    private static final RowMapper<FieldWorkItemRow> ITEM_ROW_MAPPER = JdbcFieldWorkRepository::mapItem;

    private static final RowMapper<FieldWorkStatusHistoryRow> STATUS_HISTORY_ROW_MAPPER =
            (rs, n) -> new FieldWorkStatusHistoryRow(
                    rs.getLong("id"),
                    rs.getLong("operation_id"),
                    rs.getString("status"),
                    rs.getString("status_title_ru"),
                    rs.getTimestamp("changed_at").toLocalDateTime(),
                    toLong(rs.getObject("user_id")),
                    rs.getString("last_name"),
                    rs.getString("first_name"),
                    rs.getString("note")
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcFieldWorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FieldWorkCodeRow> listStatuses() {
        return jdbcTemplate.query(LIST_STATUSES, CODE_ROW_MAPPER);
    }

    @Override
    public List<FieldWorkCodeRow> listCategories() {
        return jdbcTemplate.query(LIST_CATEGORIES, CODE_ROW_MAPPER);
    }

    @Override
    public boolean statusExists(String code) {
        Boolean ok = jdbcTemplate.queryForObject(STATUS_EXISTS, Boolean.class, code);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public boolean categoryExists(String code) {
        Boolean ok = jdbcTemplate.queryForObject(CATEGORY_EXISTS, Boolean.class, code);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public boolean operationBelongsToOrganization(long operationId, long organizationId) {
        Boolean ok = jdbcTemplate.queryForObject(BELONGS_TO_ORG, Boolean.class, operationId, organizationId);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public List<FieldWorkItemRow> listByField(long fieldId, long organizationId) {
        return jdbcTemplate.query(LIST_BY_FIELD, ITEM_ROW_MAPPER, fieldId, organizationId);
    }

    @Override
    public Optional<FieldWorkItemRow> findById(long operationId, long organizationId) {
        return jdbcTemplate.query(FIND_BY_ID, ITEM_ROW_MAPPER, operationId, organizationId)
                .stream()
                .findFirst();
    }

    @Override
    public List<FieldWorkStatusHistoryRow> listStatusHistory(long operationId) {
        return jdbcTemplate.query(LIST_STATUS_HISTORY, STATUS_HISTORY_ROW_MAPPER, operationId);
    }

    @Override
    public long insertOperation(
            long fieldId,
            long organizationId,
            long userId,
            String name,
            String category,
            String status,
            LocalDateTime operationAt) {
        Long id = jdbcTemplate.queryForObject(
                INSERT_OPERATION,
                Long.class,
                fieldId,
                organizationId,
                userId,
                operationAt,
                name,
                category,
                status);
        if (id == null) {
            throw new IllegalStateException("Не удалось создать операцию по полю");
        }
        return id;
    }

    @Override
    public void insertStatusHistory(long operationId, String status, long userId, String note) {
        jdbcTemplate.update(INSERT_STATUS_HISTORY, operationId, status, userId, note);
    }

    @Override
    public int updateOperation(
            long operationId,
            long organizationId,
            String name,
            String category,
            String status,
            LocalDateTime operationAt) {
        return jdbcTemplate.update(
                UPDATE_OPERATION,
                name,
                category,
                status,
                operationAt,
                operationId,
                organizationId);
    }

    @Override
    public int deleteOperation(long operationId, long organizationId) {
        return jdbcTemplate.update(DELETE_OPERATION, operationId, organizationId);
    }

    private static FieldWorkItemRow mapItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FieldWorkItemRow(
                rs.getLong("id"),
                rs.getLong("field_id"),
                rs.getLong("organization_id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("category_title_ru"),
                rs.getString("status"),
                rs.getString("status_title_ru"),
                rs.getTimestamp("operation_at").toLocalDateTime(),
                toLong(rs.getObject("user_id")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
