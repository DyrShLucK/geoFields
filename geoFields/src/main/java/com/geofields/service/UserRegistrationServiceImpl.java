package com.geofields.service;

import com.geofields.exception.RegistrationException;
import com.geofields.repository.OrgRegistrationInviteRepository;
import com.geofields.repository.UserAccountRepository;
import com.geofields.security.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final OrgRegistrationInviteRepository inviteRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationServiceImpl(
            UserAccountRepository userAccountRepository,
            OrgRegistrationInviteRepository inviteRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.inviteRepository = inviteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void registerWithInvite(
            String login,
            String email,
            String lastName,
            String firstName,
            String middleName,
            String password,
            String passwordConfirm,
            String inviteToken) {
        String cleanLogin = login == null ? "" : login.trim();
        String cleanEmail = email == null ? "" : email.trim();
        String cleanLast = lastName == null ? "" : lastName.trim();
        String cleanFirst = firstName == null ? "" : firstName.trim();
        String cleanMiddle = middleName == null ? "" : middleName.trim();
        String token = inviteToken == null ? "" : inviteToken.trim();

        if (!password.equals(passwordConfirm)) {
            throw new RegistrationException("Пароли не совпадают.");
        }
        if (token.isEmpty()) {
            throw new RegistrationException("Нужна ссылка-приглашение (параметр ref). Попросите у менеджера вашей организации.");
        }
        if (cleanLast.isEmpty()) {
            throw new RegistrationException("Укажите фамилию.");
        }
        if (cleanFirst.isEmpty()) {
            throw new RegistrationException("Укажите имя.");
        }

        long organizationId = inviteRepository.lockActiveOrganizationIdByToken(token)
                .orElseThrow(() -> new RegistrationException(
                        "Приглашение недействительно, уже использовано или отозвано. Запросите новую ссылку."));

        if (userAccountRepository.existsByLogin(cleanLogin)) {
            throw new RegistrationException("Такой логин уже занят.");
        }
        if (userAccountRepository.existsByEmail(cleanEmail)) {
            throw new RegistrationException("Такой email уже зарегистрирован.");
        }

        userAccountRepository.insertPendingUser(
                cleanLogin,
                cleanEmail,
                passwordEncoder.encode(password),
                organizationId,
                UserRole.USER,
                cleanLast,
                cleanFirst,
                cleanMiddle);

        if (inviteRepository.markConsumed(token) != 1) {
            throw new RegistrationException("Не удалось завершить регистрацию по приглашению. Попробуйте ещё раз.");
        }
    }
}
