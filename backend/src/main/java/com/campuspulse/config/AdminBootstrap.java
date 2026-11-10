package com.campuspulse.config;

import com.campuspulse.repository.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gives the SUPER_ADMIN (platform admin) role to the emails listed in ADMIN_EMAILS
 * (comma separated). Runs at startup and is also used on registration.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository appUserRepository;
    private final Set<String> adminEmails;

    public AdminBootstrap(
            AppUserRepository appUserRepository,
            @Value("${app.admin-emails:}") String adminEmails) {
        this.appUserRepository = appUserRepository;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdminEmail(String email) {
        return email != null
                && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public void run(ApplicationArguments args) {
        // Accounts created before roles existed ("USER") become FACULTY.
        appUserRepository.findAll().stream()
                .filter(user -> "USER".equals(user.getRole()))
                .forEach(user -> {
                    user.setRole("FACULTY");
                    appUserRepository.save(user);
                });

        appUserRepository.findAll().stream()
                .filter(user -> isAdminEmail(user.getEmail()))
                .filter(user -> !"SUPER_ADMIN".equals(user.getRole()))
                .forEach(user -> {
                    user.setRole("SUPER_ADMIN");
                    appUserRepository.save(user);
                    log.info("Promoted user id {} to SUPER_ADMIN", user.getId());
                });
    }
}
