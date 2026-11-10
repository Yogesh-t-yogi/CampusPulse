package com.campuspulse.controller.admin;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.repository.user.AppUserRepository;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.auth.TempPasswords;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.syllabus.SyllabusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
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
    private final PasswordEncoder passwordEncoder;
    private final SyllabusRepository syllabusRepository;
    private final SyllabusService syllabusService;
    private final JobPostingRepository jobPostingRepository;
    private final JobPostingService jobPostingService;

    public AdminUserController(
            AppUserRepository appUserRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder,
            SyllabusRepository syllabusRepository,
            SyllabusService syllabusService,
            JobPostingRepository jobPostingRepository,
            JobPostingService jobPostingService) {
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.syllabusRepository = syllabusRepository;
        this.syllabusService = syllabusService;
        this.jobPostingRepository = jobPostingRepository;
        this.jobPostingService = jobPostingService;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return appUserRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getId))
                .map(AdminUserController::toView)
                .toList();
    }

    /** Create an account; the one-time password is returned once in the response. */
    @PostMapping
    public Map<String, Object> createUser(@RequestBody Map<String, String> body) {
        String name = required(body.get("name"), "Name");
        String email = required(body.get("email"), "Email").toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        String password = TempPasswords.generate();
        AppUser user = new AppUser();
        user.setName(name);
        user.setEmail(email);
        user.setRole(role(body.getOrDefault("role", "STUDENT")));
        user.setPassword(passwordEncoder.encode(password));
        Map<String, Object> view = toView(appUserRepository.save(user));
        view.put("temporaryPassword", password);
        return view;
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> changeRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        AppUser user = requireOther(id);
        user.setRole(role(body.getOrDefault("role", "")));
        return toView(appUserRepository.save(user));
    }

    /** New one-time password for a user (shown once). */
    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id) {
        AppUser user = requireOther(id);
        String password = TempPasswords.generate();
        user.setPassword(passwordEncoder.encode(password));
        Map<String, Object> view = toView(appUserRepository.save(user));
        view.put("temporaryPassword", password);
        return view;
    }

    /** Delete an account with its own syllabi and private jobs. Shared catalog jobs stay. */
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        AppUser user = requireOther(id);
        AppUser admin = currentUserService.getCurrentUser();
        for (Syllabus syllabus : syllabusRepository.findAllByOwner_Id(id)) {
            syllabusService.delete(syllabus.getId(), id);
        }
        for (JobPosting job : jobPostingRepository.findAllByOwner_Id(id)) {
            jobPostingService.deleteJob(job.getId(), admin);
        }
        appUserRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    private AppUser requireOther(Long id) {
        if (currentUserService.getCurrentUser().getId().equals(id)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You can't change or delete your own account here.");
        }
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static String role(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role must be ADMIN, FACULTY or STUDENT.");
        }
        return role;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required.");
        }
        return value.trim();
    }

    private static Map<String, Object> toView(AppUser user) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", user.getId());
        view.put("name", user.getName());
        view.put("email", user.getEmail());
        view.put("role", user.getRole());
        view.put("createdAt", String.valueOf(user.getCreatedAt()));
        return view;
    }
}
