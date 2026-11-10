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

    public AppUser register(
            String name,
            String email,
            String password) {

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
        user.setRole(adminBootstrap.isAdminEmail(email) ? "ADMIN" : "USER");

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
