package com.campuspulse.config;

import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.repository.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local/demo only: when DEMO_USERS_PASSWORD is set, makes sure one account per role
 * exists. Existing accounts are left alone (password is not reset).
 * Leave DEMO_USERS_PASSWORD unset in any shared or real deployment.
 */
@Component
@Order(10)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private record DemoUser(String name, String email, String role) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("Demo Platform Admin", "superadmin@campuspulse.local", "SUPER_ADMIN"),
            new DemoUser("Demo Admin", "admin@campuspulse.local", "ADMIN"),
            new DemoUser("Demo HOD", "hod@campuspulse.local", "HOD"),
            new DemoUser("Demo Faculty", "faculty@campuspulse.local", "FACULTY"),
            new DemoUser("Demo Student", "student@campuspulse.local", "STUDENT"));

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final String password;

    public DemoUserSeeder(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            @Value("${app.demo-users-password:}") String password) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            return;
        }
        for (DemoUser demo : DEMO_USERS) {
            if (appUserRepository.existsByEmail(demo.email())) {
                continue;
            }
            AppUser user = new AppUser();
            user.setName(demo.name());
            user.setEmail(demo.email());
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(demo.role());
            // Demo accounts live in the default college, department "General".
            institutionRepository.findByCodeIgnoreCase("DEFAULT").ifPresent(college -> {
                user.setInstitutionId(college.getId());
                departmentRepository.findAllByInstitutionIdOrderByNameAsc(college.getId()).stream()
                        .filter(department -> "GEN".equals(department.getCode()))
                        .findFirst()
                        .ifPresent(department -> user.setDepartmentId(department.getId()));
            });
            appUserRepository.save(user);
            log.info("Created demo account {} ({})", demo.email(), demo.role());
        }
    }
}
