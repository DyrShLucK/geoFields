package com.geofields.repository;

import com.geofields.model.Fields;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
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
    void findAllFields_mapsDatabaseRowsToFieldsModel() throws Exception {
        JdbcFieldRepository repository = new JdbcFieldRepository(jdbcTemplate);

        // Мокаем jdbcTemplate так, чтобы проверить и SQL, и маппинг ResultSet -> FieldRow.
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<Fields> rowMapper = invocation.getArgument(1);

            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(10L);
            when(rs.getString("field_name")).thenReturn("North field");
            when(rs.getBigDecimal("field_area")).thenReturn(new BigDecimal("12.34"));
            when(rs.getString("geometry")).thenReturn("{\"type\":\"Polygon\",\"coordinates\":[[[1,2],[3,4],[1,2]]]}");

            return List.of(rowMapper.mapRow(rs, 0));
        });

        List<Fields> rows = repository.findAllFields();

        // Проверяем, что значения из БД действительно попали в DTO-строку.
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId()).isEqualTo(10L);
        assertThat(rows.getFirst().getField_name()).isEqualTo("North field");
        assertThat(rows.getFirst().getField_area()).isEqualByComparingTo("12.34");
        assertThat(rows.getFirst().getField_geometry()).contains("\"type\":\"Polygon\"");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        // Важно: геометрия должна приходить уже в GeoJSON.
        assertThat(sqlCaptor.getValue()).contains("ST_AsGeoJSON(geom)");
        assertThat(sqlCaptor.getValue()).contains("ORDER BY id");
    }
}
