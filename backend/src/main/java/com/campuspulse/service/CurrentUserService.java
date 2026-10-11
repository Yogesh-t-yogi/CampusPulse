package com.campuspulse.service;

import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AppUser getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException(
                    "User is not authenticated");
        }

        Object principal = authentication.getPrincipal();

        // The JWT filter stores the authenticated AppUser as principal.
        if (principal instanceof AppUser appUser) {
            return appUser;
        }

        // Fallback for authentication implementations using an email string.
        String email = authentication.getName();

        return appUserRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));
    }
}
