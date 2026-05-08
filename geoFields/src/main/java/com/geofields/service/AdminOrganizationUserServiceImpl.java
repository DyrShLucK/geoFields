package com.geofields.service;

import com.geofields.dto.orgmanager.ActionResult;
import com.geofields.repository.UserAccountRepository;
import com.geofields.repository.row.OrganizationMemberRow;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import org.springframework.stereotype.Service;

@Service
public class AdminOrganizationUserServiceImpl implements AdminOrganizationUserService {

    private final UserAccountRepository userAccountRepository;

    public AdminOrganizationUserServiceImpl(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public ActionResult updateMemberRole(GeoFieldsUserDetails admin, long targetUserId, UserRole newRole) {
        if (admin.getUserId() == targetUserId) {
            return new ActionResult(false, "Нельзя изменить свою роль.");
        }
        long orgId = admin.getOrganizationId();
        return userAccountRepository
                .findMember(orgId, targetUserId)
                .map(target -> applyRoleChange(orgId, target, newRole))
                .orElseGet(() -> new ActionResult(false, "Пользователь не найден в организации."));
    }

    private ActionResult applyRoleChange(long orgId, OrganizationMemberRow target, UserRole newRole) {
        if (target.role() == UserRole.ORG_ADMIN && newRole != UserRole.ORG_ADMIN) {
            if (userAccountRepository.countApprovedAdmins(orgId) <= 1) {
                return new ActionResult(false, "Нельзя снять роль администратора с последнего администратора организации.");
            }
        }
        int n = userAccountRepository.updateUserRole(target.id(), orgId, newRole);
        return new ActionResult(n == 1, n == 1 ? "Роль обновлена." : "Не удалось обновить роль.");
    }

    @Override
    public ActionResult deleteMember(GeoFieldsUserDetails admin, long targetUserId) {
        if (admin.getUserId() == targetUserId) {
            return new ActionResult(false, "Нельзя удалить свою учётную запись.");
        }
        long orgId = admin.getOrganizationId();
        return userAccountRepository
                .findMember(orgId, targetUserId)
                .map(target -> applyDelete(orgId, target))
                .orElseGet(() -> new ActionResult(false, "Пользователь не найден в организации."));
    }

    private ActionResult applyDelete(long orgId, OrganizationMemberRow target) {
        if (target.role() == UserRole.ORG_ADMIN && userAccountRepository.countApprovedAdmins(orgId) <= 1) {
            return new ActionResult(false, "Нельзя удалить последнего администратора организации.");
        }
        int n = userAccountRepository.deleteUser(target.id(), orgId);
        return new ActionResult(n == 1, n == 1 ? "Пользователь удалён." : "Не удалось удалить пользователя.");
    }
}
