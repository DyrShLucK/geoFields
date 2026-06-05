package com.geofields.repository;
import com.geofields.repository.row.FieldHistoryRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void findAllFields_mapsJoinedRowsToHistoryModel() throws Exception {
        JdbcFieldRepository repository = new JdbcFieldRepository(jdbcTemplate);
        List<FieldHistoryRow> expected = List.of(
                new FieldHistoryRow(
                        10L,
                        "North field",
                        new BigDecimal("12.34"),
                        true,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[1,2],[3,4],[1,2]]]}",
                        100L,
                        5L,
                        "Wheat",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        2024
                )
        );

        // Мокаем jdbcTemplate и проверяем, что репозиторий дергает нужный SQL.
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class), any(RowMapper.class))).thenReturn(expected);

        List<FieldHistoryRow> rows = repository.findAllFieldsWithHistory(1L);

        // Проверяем, что значения из БД действительно попали в строку истории.
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().fieldId()).isEqualTo(10L);
        assertThat(rows.getFirst().fieldName()).isEqualTo("North field");
        assertThat(rows.getFirst().fieldArea()).isEqualByComparingTo("12.34");
        assertThat(rows.getFirst().geometryJson()).contains("\"type\":\"Polygon\"");
        assertThat(rows.getFirst().fieldCropId()).isEqualTo(100L);
        assertThat(rows.getFirst().cropName()).isEqualTo("Wheat");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(org.springframework.jdbc.core.PreparedStatementSetter.class), any(RowMapper.class));
        // Важно: геометрия должна приходить в GeoJSON и быть join по истории.
        assertThat(sqlCaptor.getValue()).contains("ST_AsGeoJSON(f.geom)");
        assertThat(sqlCaptor.getValue()).contains("INNER JOIN field_crops");
        assertThat(sqlCaptor.getValue()).contains("ORDER BY f.id");
        assertThat(sqlCaptor.getValue()).contains("organization_id = ?");
    }
}
