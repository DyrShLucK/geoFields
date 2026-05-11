package com.geofields.service;

import com.geofields.dto.orgmanager.ActionResult;
import com.geofields.repository.UserAccountRepository;
import com.geofields.repository.row.OrganizationMemberRow;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrganizationUserServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void updateMemberRole_rejectsSelfRoleChange() {
        AdminOrganizationUserServiceImpl service = new AdminOrganizationUserServiceImpl(userAccountRepository);
        GeoFieldsUserDetails admin = adminUser(100L, 5L);

        ActionResult result = service.updateMemberRole(admin, 100L, UserRole.AGRONOMIST);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("Нельзя изменить свою роль");
        verify(userAccountRepository, never()).findMember(5L, 100L);
    }

    @Test
    void updateMemberRole_rejectsDemotionOfLastAdmin() {
        AdminOrganizationUserServiceImpl service = new AdminOrganizationUserServiceImpl(userAccountRepository);
        GeoFieldsUserDetails admin = adminUser(100L, 5L);
        OrganizationMemberRow target = member(200L, UserRole.ORG_ADMIN);

        when(userAccountRepository.findMember(5L, 200L)).thenReturn(Optional.of(target));
        when(userAccountRepository.countApprovedAdmins(5L)).thenReturn(1L);

        ActionResult result = service.updateMemberRole(admin, 200L, UserRole.AGRONOMIST);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("последнего администратора");
        verify(userAccountRepository, never()).updateUserRole(200L, 5L, UserRole.AGRONOMIST);
    }

    @Test
    void updateMemberRole_updatesRoleWhenAllowed() {
        AdminOrganizationUserServiceImpl service = new AdminOrganizationUserServiceImpl(userAccountRepository);
        GeoFieldsUserDetails admin = adminUser(100L, 5L);
        OrganizationMemberRow target = member(300L, UserRole.USER);

        when(userAccountRepository.findMember(5L, 300L)).thenReturn(Optional.of(target));
        when(userAccountRepository.updateUserRole(300L, 5L, UserRole.AGRONOMIST)).thenReturn(1);

        ActionResult result = service.updateMemberRole(admin, 300L, UserRole.AGRONOMIST);

        assertThat(result.ok()).isTrue();
        assertThat(result.message()).contains("Роль обновлена");
        verify(userAccountRepository).updateUserRole(300L, 5L, UserRole.AGRONOMIST);
    }

    @Test
    void deleteMember_rejectsDeleteOfLastAdmin() {
        AdminOrganizationUserServiceImpl service = new AdminOrganizationUserServiceImpl(userAccountRepository);
        GeoFieldsUserDetails admin = adminUser(100L, 5L);
        OrganizationMemberRow target = member(400L, UserRole.ORG_ADMIN);

        when(userAccountRepository.findMember(5L, 400L)).thenReturn(Optional.of(target));
        when(userAccountRepository.countApprovedAdmins(5L)).thenReturn(1L);

        ActionResult result = service.deleteMember(admin, 400L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("последнего администратора");
        verify(userAccountRepository, never()).deleteUser(400L, 5L);
    }

    private static GeoFieldsUserDetails adminUser(long userId, long orgId) {
        return new GeoFieldsUserDetails(
                userId,
                orgId,
                "admin",
                "hash",
                UserRole.ORG_ADMIN,
                true,
                "Adminov",
                "Admin",
                "");
    }

    private static OrganizationMemberRow member(long id, UserRole role) {
        return new OrganizationMemberRow(
                id,
                "member" + id,
                "u" + id + "@mail.local",
                "Last",
                "First",
                "",
                role,
                "APPROVED",
                true);
    }
}
