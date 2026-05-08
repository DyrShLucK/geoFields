package com.geofields.dto.orgadmin;

import com.geofields.dto.orgmanager.CsrfInfo;

import java.util.List;

public record AdminSummaryResponse(
        long organizationId,
        String organizationName,
        String currentLogin,
        String currentUserFullName,
        List<OrgMemberItem> members,
        CsrfInfo csrf) {
}
