package com.geofields.repository;

import com.geofields.security.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository {

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    void insertUser(
            String login,
            String email,
            String passwordHash,
            long organizationId,
            UserRole role,
            String lastName,
            String firstName,
            String middleName);

    void insertPendingUser(
            String login,
            String email,
            String passwordHash,
            long organizationId,
            UserRole role,
            String lastName,
            String firstName,
            String middleName);

    List<PendingRegistrationRow> findPendingRegistrations(long organizationId);

    int approveUser(long userId, long organizationId);

    int rejectUser(long userId, long organizationId);

    List<OrganizationMemberRow> findMembersByOrganization(long organizationId);

    Optional<OrganizationMemberRow> findMember(long organizationId, long userId);

    int updateUserRole(long userId, long organizationId, UserRole role);

    int deleteUser(long userId, long organizationId);

    long countApprovedAdmins(long organizationId);
}
