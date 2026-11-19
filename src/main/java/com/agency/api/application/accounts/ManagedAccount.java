package com.agency.api.application.accounts;

import com.agency.api.domain.Account;

public record ManagedAccount(Account account, String temporaryPassword) { }
