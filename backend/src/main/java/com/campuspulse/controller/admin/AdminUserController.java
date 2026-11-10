package com.campuspulse.controller.admin;

import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.user.AppUserRepository;
import com.campuspulse.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ADMIN only (SecurityConfig: /api/admin/**).
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private static final Set<String> ROLES = Set.of("ADMIN", "FACULTY", "STUDENT");

    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;

    public AdminUserController(
            AppUserRepository appUserRepository,
            CurrentUserService currentUserService) {
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return appUserRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getId))
                .map(AdminUserController::toView)
                .toList();
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> changeRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String role = body.getOrDefault("role", "").trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role must be ADMIN, FACULTY or STUDENT.");
        }
        if (currentUserService.getCurrentUser().getId().equals(id)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You can't change your own role.");
        }
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(role);
        return toView(appUserRepository.save(user));
    }

    private static Map<String, Object> toView(AppUser user) {
        return Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "createdAt", String.valueOf(user.getCreatedAt()));
    }
}
