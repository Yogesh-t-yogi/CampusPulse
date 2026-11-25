package com.campuspulse.service.auth;

import com.campuspulse.config.AdminBootstrap;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.repository.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminBootstrap adminBootstrap;
    private final InstitutionRepository institutionRepository;
    /** false (default): only HOD / college admin create accounts; the platform admin is created at startup. */
    private final boolean selfRegistration;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AdminBootstrap adminBootstrap,
            InstitutionRepository institutionRepository,
            @org.springframework.beans.factory.annotation.Value("${app.self-registration:false}") boolean selfRegistration) {

        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.adminBootstrap = adminBootstrap;
        this.institutionRepository = institutionRepository;
        this.selfRegistration = selfRegistration;
    }

    /** Message shown when sign-up is closed. */
    public static final String REGISTRATION_CLOSED =
            "Accounts are created by your HOD or college admin, with your department already set. Ask them for your login.";

    public boolean selfRegistrationOpen() {
        return selfRegistration;
    }

    /** Roles a person can pick when signing up. ADMIN only via ADMIN_EMAILS. */
    public static final java.util.Set<String> SELF_SERVICE_ROLES =
            java.util.Set.of("FACULTY", "STUDENT");

    public AppUser register(
            String name,
            String email,
            String password,
            String requestedRole,
            String institutionCode) {

        if (!selfRegistration) {
            throw new IllegalArgumentException(REGISTRATION_CLOSED);
        }
        String role = requestedRole == null
                ? "FACULTY"
                : requestedRole.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SELF_SERVICE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Choose Faculty or Student.");
        }

        if (appUserRepository.existsByEmail(email)) {
            throw new RuntimeException(
                    "Email already registered"
            );
        }

        AppUser user = new AppUser();

        user.setName(name);
        user.setEmail(email);
        user.setPassword(
                passwordEncoder.encode(password)
        );
        user.setRole(adminBootstrap.isAdminEmail(email) ? "SUPER_ADMIN" : role);

        // College by code (blank = the default college). Department is set later by an admin/HOD.
        String code = institutionCode == null || institutionCode.isBlank() ? "DEFAULT" : institutionCode.trim();
        user.setInstitutionId(institutionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown college code: " + code))
                .getId());

        return appUserRepository.save(user);
    }

    public boolean mustChangePassword(String email) {
        return findUser(email).map(AppUser::isMustChangePassword).orElse(false);
    }

    /** Emails are stored lower-case by the HOD / admin screens; older self sign-ups may keep their case. */
    private java.util.Optional<AppUser> findUser(String email) {
        String trimmed = email == null ? "" : email.trim();
        return appUserRepository.findByEmail(trimmed)
                .or(() -> appUserRepository.findByEmail(trimmed.toLowerCase(java.util.Locale.ROOT)));
    }

    public void changePassword(AppUser user, String currentPassword, String newPassword) {
        AppUser fresh = appUserRepository.findById(user.getId()).orElseThrow();
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, fresh.getPassword())) {
            throw new IllegalArgumentException("Your current password is wrong.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("The new password needs at least 8 characters.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("Choose a password different from the one-time password.");
        }
        fresh.setPassword(passwordEncoder.encode(newPassword));
        fresh.setMustChangePassword(false);
        appUserRepository.save(fresh);
    }

    public String login(
            String email,
            String password) {

        AppUser user = findUser(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Invalid email or password"
                        )
                );

        if (!passwordEncoder.matches(
                password,
                user.getPassword())) {

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        return jwtService.generateToken(user.getEmail());
    }

   

}
