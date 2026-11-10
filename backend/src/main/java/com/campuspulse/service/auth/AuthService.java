package com.campuspulse.service.auth;

import com.campuspulse.config.AdminBootstrap;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminBootstrap adminBootstrap;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AdminBootstrap adminBootstrap) {

        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.adminBootstrap = adminBootstrap;
    }

    /** Roles a person can pick when signing up. ADMIN only via ADMIN_EMAILS. */
    public static final java.util.Set<String> SELF_SERVICE_ROLES =
            java.util.Set.of("FACULTY", "STUDENT");

    public AppUser register(
            String name,
            String email,
            String password,
            String requestedRole) {

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
        user.setRole(adminBootstrap.isAdminEmail(email) ? "ADMIN" : role);

        return appUserRepository.save(user);
    }

    public String login(
            String email,
            String password) {

        AppUser user = appUserRepository
                .findByEmail(email)
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
