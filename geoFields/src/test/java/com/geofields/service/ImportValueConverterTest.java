package com.geofields.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ImportValueConverterTest {

    @Test
    void parseDate_parsesIsoDate() {
        assertThat(ImportValueConverter.parseDate("2024-05-10")).isEqualTo(LocalDate.of(2024, 5, 10));
    }

    @Test
    void parseDate_returnsNullForBlankOrInvalid() {
        assertThat(ImportValueConverter.parseDate(null)).isNull();
        assertThat(ImportValueConverter.parseDate("")).isNull();
        assertThat(ImportValueConverter.parseDate("  ")).isNull();
        assertThat(ImportValueConverter.parseDate("not-a-date")).isNull();
    }

    @Test
    void toBigDecimal_convertsOrReturnsNull() {
        assertThat(ImportValueConverter.toBigDecimal(12.5)).isEqualByComparingTo(BigDecimal.valueOf(12.5));
        assertThat(ImportValueConverter.toBigDecimal(null)).isNull();
    }
}
