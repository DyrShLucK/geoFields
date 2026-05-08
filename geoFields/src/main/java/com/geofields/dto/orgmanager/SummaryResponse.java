package com.geofields.dto.orgmanager;

import java.util.List;

public record SummaryResponse(
        long organizationId,
        String organizationName,
        String currentLogin,
        String currentUserFullName,
        List<PendingItem> pending,
        List<InviteItem> invites,
        CsrfInfo csrf) {
}
