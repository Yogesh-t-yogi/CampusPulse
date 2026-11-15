package com.campuspulse.controller.auth;

import com.campuspulse.entity.user.AppUser;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.service.auth.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;

    public AuthController(
            AuthService authService,
            CurrentUserService currentUserService,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            ProgramRepository programRepository) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
    }

    // Who is logged in (used by the frontend to show admin-only tools).
    // Set a new password (required after a one-time password). Clears mustChangePassword.
    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> request) {
        authService.changePassword(
                currentUserService.getCurrentUser(),
                request.get("currentPassword"),
                request.get("newPassword"));
        return Map.of("message", "Password changed");
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AppUser user = currentUserService.getCurrentUser();
        Map<String, Object> view = new java.util.HashMap<>();
        view.put("id", user.getId());
        view.put("name", user.getName());
        view.put("email", user.getEmail());
        view.put("role", user.getRole());
        view.put("mustChangePassword", user.isMustChangePassword());
        view.put("rollNumber", user.getRollNumber());
        view.put("institution", user.getInstitutionId() == null ? null
                : institutionRepository.findById(user.getInstitutionId())
                    .map(i -> Map.of("id", i.getId(), "name", i.getName(), "code", i.getCode()))
                    .orElse(null));
        view.put("department", user.getDepartmentId() == null ? null
                : departmentRepository.findById(user.getDepartmentId())
                    .map(d -> Map.of("id", d.getId(), "name", d.getName(), "code", d.getCode()))
                    .orElse(null));
        view.put("program", user.getProgramId() == null ? null
                : programRepository.findById(user.getProgramId())
                    .map(p -> Map.of("id", p.getId(), "name", p.getName(), "code", p.getCode()))
                    .orElse(null));
        return view;
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
                    password,
                    request.get("role"),
                    request.get("institutionCode")
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

            boolean mustChange = authService.mustChangePassword(email.trim());
            return ResponseEntity.ok(
                    Map.of(
                            "message", "Login successful",
                            "token", token,
                            "tokenType", "Bearer",
                            "mustChangePassword", mustChange
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