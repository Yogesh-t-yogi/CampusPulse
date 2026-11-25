package com.campuspulse.config;

import com.campuspulse.entity.user.AppUser;

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

    private final org.springframework.beans.factory.ObjectProvider<org.springframework.security.crypto.password.PasswordEncoder> encoder;
    private final org.springframework.beans.factory.ObjectProvider<com.campuspulse.repository.org.InstitutionRepository> institutions;
    private final String defaultPassword;

    public AdminBootstrap(
            AppUserRepository appUserRepository,
            @Value("${app.admin-emails:}") String adminEmails,
            @Value("${app.default-user-password:}") String defaultPassword,
            org.springframework.beans.factory.ObjectProvider<org.springframework.security.crypto.password.PasswordEncoder> encoder,
            org.springframework.beans.factory.ObjectProvider<com.campuspulse.repository.org.InstitutionRepository> institutions) {
        this.appUserRepository = appUserRepository;
        this.defaultPassword = defaultPassword == null ? "" : defaultPassword.trim();
        this.encoder = encoder;
        this.institutions = institutions;
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

        // Sign-up is closed, so the platform admin account is created here (first start).
        for (String email : adminEmails) {
            if (appUserRepository.existsByEmail(email)) {
                continue;
            }
            if (defaultPassword.length() < 8) {
                log.warn("ADMIN_EMAILS has an email with no account. Set DEFAULT_USER_PASSWORD (8+ characters) "
                        + "to create the platform admin account at startup.");
                continue;
            }
            AppUser admin = new AppUser();
            admin.setName("Platform admin");
            admin.setEmail(email);
            admin.setRole("SUPER_ADMIN");
            admin.setPassword(encoder.getObject().encode(defaultPassword));
            admin.setMustChangePassword(true);
            institutions.getObject().findByCodeIgnoreCase("DEFAULT").ifPresent(college -> admin.setInstitutionId(college.getId()));
            appUserRepository.save(admin);
            log.info("Created the platform admin account (id {}); it must change the default password at first sign-in.", admin.getId());
        }

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
