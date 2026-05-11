package com.geofields.service;

import com.geofields.exception.RegistrationException;
import com.geofields.repository.OrgRegistrationInviteRepository;
import com.geofields.repository.UserAccountRepository;
import com.geofields.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OrgRegistrationInviteRepository inviteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void registerWithInvite_insertsPendingUserAndConsumesInvite_whenDataIsValid() {
        UserRegistrationServiceImpl service = new UserRegistrationServiceImpl(
                userAccountRepository,
                inviteRepository,
                passwordEncoder);

        when(inviteRepository.lockActiveOrganizationIdByToken("token-1")).thenReturn(Optional.of(77L));
        when(userAccountRepository.existsByLogin("new_login")).thenReturn(false);
        when(userAccountRepository.existsByEmail("new@mail.local")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded-pass");
        when(inviteRepository.markConsumed("token-1")).thenReturn(1);

        service.registerWithInvite(
                "  new_login  ",
                "  new@mail.local  ",
                "  Ivanov  ",
                "  Ivan  ",
                "  Ivanovich  ",
                "pass123",
                "pass123",
                "  token-1  ");

        verify(userAccountRepository).insertPendingUser(
                "new_login",
                "new@mail.local",
                "encoded-pass",
                77L,
                UserRole.USER,
                "Ivanov",
                "Ivan",
                "Ivanovich");
        verify(inviteRepository).markConsumed("token-1");
    }

    @Test
    void registerWithInvite_throwsWhenPasswordsDoNotMatch() {
        UserRegistrationServiceImpl service = new UserRegistrationServiceImpl(
                userAccountRepository,
                inviteRepository,
                passwordEncoder);

        assertThatThrownBy(() -> service.registerWithInvite(
                "u", "e@mail", "L", "F", "", "pass1", "pass2", "token"))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("Пароли не совпадают");

        verify(inviteRepository, never()).lockActiveOrganizationIdByToken("token");
    }

    @Test
    void registerWithInvite_throwsWhenInviteIsInvalid() {
        UserRegistrationServiceImpl service = new UserRegistrationServiceImpl(
                userAccountRepository,
                inviteRepository,
                passwordEncoder);

        when(inviteRepository.lockActiveOrganizationIdByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerWithInvite(
                "u", "e@mail", "L", "F", "", "pass", "pass", "bad-token"))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("Приглашение недействительно");

        verify(userAccountRepository, never()).insertPendingUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registerWithInvite_throwsWhenInviteConsumptionFails() {
        UserRegistrationServiceImpl service = new UserRegistrationServiceImpl(
                userAccountRepository,
                inviteRepository,
                passwordEncoder);

        when(inviteRepository.lockActiveOrganizationIdByToken("token-2")).thenReturn(Optional.of(55L));
        when(userAccountRepository.existsByLogin("login2")).thenReturn(false);
        when(userAccountRepository.existsByEmail("login2@mail.local")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("encoded");
        when(inviteRepository.markConsumed("token-2")).thenReturn(0);

        assertThatThrownBy(() -> service.registerWithInvite(
                "login2", "login2@mail.local", "Last", "First", "", "pass", "pass", "token-2"))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("Не удалось завершить регистрацию");
    }
}
