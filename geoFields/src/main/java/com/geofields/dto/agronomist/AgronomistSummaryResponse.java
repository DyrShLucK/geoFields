package com.geofields.dto.agronomist;

import com.geofields.dto.orgmanager.CsrfInfo;

import java.util.List;

public record AgronomistSummaryResponse(
        long organizationId,
        String organizationName,
        String currentLogin,
        String currentUserFullName,
        List<FieldOptionItem> fields,
        List<CropOptionItem> crops,
        CsrfInfo csrf
) {
}
