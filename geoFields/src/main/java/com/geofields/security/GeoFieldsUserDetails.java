package com.geofields.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Обёртка над строкой из users: id, организация, роль, ФИО. */
public class GeoFieldsUserDetails implements UserDetails {

    private final Long userId;
    private final Long organizationId;
    private final String login;
    private final String passwordHash;
    private final UserRole role;
    private final boolean active;
    private final String lastName;
    private final String firstName;
    private final String middleName;

    public GeoFieldsUserDetails(
            Long userId,
            Long organizationId,
            String login,
            String passwordHash,
            UserRole role,
            boolean active,
            String lastName,
            String firstName,
            String middleName) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.lastName = lastName != null ? lastName : "";
        this.firstName = firstName != null ? firstName : "";
        this.middleName = middleName != null ? middleName : "";
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public UserRole getRole() {
        return role;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    /** Фамилия Имя Отчество; если пусто — логин. */
    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (!lastName.isBlank()) {
            sb.append(lastName.trim());
        }
        if (!firstName.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(firstName.trim());
        }
        if (!middleName.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(middleName.trim());
        }
        return sb.length() > 0 ? sb.toString() : login;
    }

    public boolean isAdmin() {
        return role == UserRole.ORG_ADMIN;
    }

    public boolean isOrgManager() {
        return role == UserRole.ORG_MANAGER;
    }

    public boolean isAgronomist() {
        return role == UserRole.AGRONOMIST;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.springAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
