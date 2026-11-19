package com.agency.api.config;

import com.agency.api.domain.*;
import com.agency.api.repository.AccountRepository;
import com.agency.api.repository.HousekeeperRepository;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/** Seeds fictional, approved candidate profiles for local matching demos only. */
@Component
@Profile("dev")
public class DemoHousekeeperSeeder implements CommandLineRunner {
    public static final String DEMO_PASSWORD = "LarLink@2026";
    private final AccountRepository accounts;
    private final HousekeeperRepository profiles;
    private final PasswordEncoder encoder;

    public DemoHousekeeperSeeder(AccountRepository accounts, HousekeeperRepository profiles, PasswordEncoder encoder) {
        this.accounts = accounts; this.profiles = profiles; this.encoder = encoder;
    }

    @Override public void run(String... args) {
        for (var sample : DemoHousekeeperCatalog.samples()) {
            String email = DemoHousekeeperCatalog.emailFor(sample.name());
            var existing = accounts.findByEmailIgnoreCase(email)
                    .or(() -> accounts.findByEmailIgnoreCase(sample.email()));
            Account account;
            if (existing.isPresent()) {
                account = existing.get();
                account.changeEmail(email);
                account.changePassword(encoder.encode(DEMO_PASSWORD));
                account = accounts.save(account);
            } else {
                account = accounts.save(new Account(sample.name(), email, encoder.encode(DEMO_PASSWORD), Role.CANDIDATE));
            }
            Account finalAccount = account;
            var profile = profiles.findByAccountId(finalAccount.getId()).orElseGet(() ->
                    new HousekeeperProfile(finalAccount, LocalDate.of(sample.birthYear(), 6, 15), sample.phone(), sample.city(), sample.district(), sample.neighborhood(), sample.skills(), sample.days(), sample.salary(), sample.experience()));
            profile.updateProfile(LocalDate.of(sample.birthYear(), 6, 15), sample.phone(), sample.city(), sample.district(), sample.neighborhood(), sample.skills(), sample.days(), sample.salary(), sample.experience());
            profile.decide("APPROVED", null);
            profiles.save(profile);
        }
    }
}
