package com.geofields.service;

public interface UserRegistrationService {

    /**
     * Регистрация только по реферальному токену (ссылка от менеджера организации).
     * Учётная запись создаётся в статусе PENDING до одобрения на /org/manager.
     */
    void registerWithInvite(
            String login,
            String email,
            String lastName,
            String firstName,
            String middleName,
            String password,
            String passwordConfirm,
            String inviteToken);
}
