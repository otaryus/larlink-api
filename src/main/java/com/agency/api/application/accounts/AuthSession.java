package com.agency.api.application.accounts;

import java.util.UUID;

public record AuthSession(String token, UUID id, String name, String email, String role, boolean mustChangePassword) { }
