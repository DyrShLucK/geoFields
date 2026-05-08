package com.geofields.service;

import com.geofields.dto.orgmanager.ActionResult;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;

public interface AdminOrganizationUserService {

    ActionResult updateMemberRole(GeoFieldsUserDetails admin, long targetUserId, UserRole newRole);

    ActionResult deleteMember(GeoFieldsUserDetails admin, long targetUserId);
}
