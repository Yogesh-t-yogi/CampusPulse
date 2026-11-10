package com.campuspulse.controller.auth;

import com.campuspulse.entity.user.AppUser;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.auth.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(
            AuthService authService,
            CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    // Who is logged in (used by the frontend to show admin-only tools).
    @GetMapping("/me")
    public Map<String, Object> me() {
        AppUser user = currentUserService.getCurrentUser();
        return Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Map<String, String> request) {

        String name = request.get("name");
        String email = request.get("email");
        String password = request.get("password");

        if (name == null || name.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Name, email and password are required"
                    ));
        }

        try {
            AppUser user = authService.register(
                    name.trim(),
                    email.trim(),
                    password
            );

            return ResponseEntity.ok(
                    Map.of(
                            "message", "User registered successfully",
                            "userId", user.getId(),
                            "name", user.getName(),
                            "email", user.getEmail(),
                            "role", user.getRole()
                    )
            );

        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            exception.getMessage()
                    ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request) {

        String email = request.get("email");
        String password = request.get("password");

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Email and password are required"
                    ));
        }

        try {
            String token = authService.login(
                    email.trim(),
                    password
            );

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Login successful",
                            "token", token,
                            "tokenType", "Bearer"
                    )
            );

        } catch (RuntimeException exception) {
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "message",
                            "Invalid email or password"
                    ));
        }
    }
}