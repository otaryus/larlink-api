package com.agency.api.application.accounts;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountManagementUseCase {
    private final AccountManagementPort accounts;

    public AccountManagementUseCase(AccountManagementPort accounts) {
        this.accounts = accounts;
    }

    @Transactional(noRollbackFor = AccountManagementException.class)
    public ManagedAccount createAgencyAccount(Account actor, String name, String email, String requestedRole,
            String phone, String address, byte[] photo, String contentType) {
        requireAgent(actor);
        if (accounts.emailExists(email))
            throw new AccountManagementException(AccountManagementException.Kind.CONFLICT,
                    "Este email já está registado.");
        Role role;
        try {
            role = switch (requestedRole.toUpperCase(Locale.ROOT)) {
                case "AGENT", "AGENCY", "GESTOR" -> Role.AGENT;
                case "SECRETARY", "SECRETARIA" -> Role.SECRETARY;
                case "CLIENT", "CLIENTE" -> Role.CLIENT;
                default -> throw new IllegalArgumentException();
            };
        } catch (Exception exception) {
            throw new AccountManagementException(AccountManagementException.Kind.INVALID,
                    "A agência pode criar contas de cliente, secretária ou gestor.");
        }
        if (role == Role.CLIENT && (phone == null || phone.isBlank() || address == null || address.trim().length() < 5))
            throw new AccountManagementException(AccountManagementException.Kind.INVALID,
                    "Para criar cliente, indique telefone e endereço.");
        if (role == Role.CLIENT && photo == null)
            throw new AccountManagementException(AccountManagementException.Kind.INVALID,
                    "Carregue uma fotografia de perfil.");
        String temporaryPassword = accounts.temporaryPassword();
        Account account = new Account(name.trim(), email, accounts.encode(temporaryPassword), role, true);
        if (photo != null)
            account.setProfilePhoto(photo, contentType);
        account = accounts.save(account);
        if (role == Role.CLIENT)
            accounts.saveClient(new ClientProfile(account, phone.trim(), address.trim()));
        accounts.record(actor, "ACCOUNT_CREATED", "Account", account.getId(), role + " " + account.getEmail());
        return new ManagedAccount(account, temporaryPassword);
    }

    @Transactional(noRollbackFor = AccountManagementException.class)
    public ManagedAccount resetTemporaryPassword(Account actor, UUID accountId) {
        requireAgent(actor);
        Account account = accounts.account(accountId)
                .orElseThrow(() -> new AccountManagementException(AccountManagementException.Kind.NOT_FOUND, ""));
        String temporaryPassword = accounts.temporaryPassword();
        account.setTemporaryPassword(accounts.encode(temporaryPassword));
        accounts.record(actor, "TEMPORARY_PASSWORD_ISSUED", "Account", accountId,
                "Credencial temporária redefinida");
        return new ManagedAccount(account, temporaryPassword);
    }

    private void requireAgent(Account actor) {
        if (actor.getRole() != Role.AGENT)
            throw new AccountManagementException(AccountManagementException.Kind.FORBIDDEN, "");
    }
}
