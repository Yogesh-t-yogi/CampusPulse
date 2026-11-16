package com.campuspulse.config;

import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.user.AppUserRepository;
import com.campuspulse.service.auth.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Locale;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC-02: role rules of the real {@link SecurityConfig} filter chain (with the real
 * {@link JwtAuthenticationFilter}; only the token parser and user lookup are mocked).
 * A catch-all probe controller answers 200, so a 200 means "security let it through".
 * No database or AI service is needed.
 */
@SpringJUnitWebConfig(SecurityRulesTest.Config.class)
class SecurityRulesTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, CorsConfig.class})
    static class Config {

        // Plain mocks, not beans (Spring would try to inject JwtService's @Value fields into a mock bean).
        static final JwtService JWT = mock(JwtService.class);
        static final AppUserRepository USERS = mock(AppUserRepository.class);

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(JWT, USERS);
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    @RestController
    static class Probe {
        @RequestMapping("/**")
        String ok() {
            return "ok";
        }
    }

    @Autowired
    WebApplicationContext context;
    private final JwtService jwtService = Config.JWT;
    private final AppUserRepository users = Config.USERS;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(jwtService, users);
        // Token "<ROLE>" belongs to <role>@college.edu with that role.
        when(jwtService.extractEmail(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).toLowerCase(Locale.ROOT) + "@college.edu");
        when(users.findByEmail(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0, String.class);
            AppUser user = new AppUser();
            user.setId(1L);
            user.setEmail(email);
            user.setRole(email.substring(0, email.indexOf('@')).toUpperCase(Locale.ROOT));
            user.setInstitutionId(1L);
            return Optional.of(user);
        });
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + role);
    }

    @ParameterizedTest(name = "TC-02 {0} on /api/resume/** -> {1}")
    @CsvSource({
            "FACULTY, 403",
            "HOD, 403",
            "STUDENT, 200",
            "ADMIN, 200",
            "SUPER_ADMIN, 200"})
    void tc02_resumeIsForStudentsAndAdmins(String role, int expected) throws Exception {
        mvc.perform(as(role, get("/api/resume/matches"))).andExpect(status().is(expected));
        mvc.perform(as(role, post("/api/resume/upload"))).andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "TC-02 {0} on /api/recommendations/** -> {1}")
    @CsvSource({
            "STUDENT, 200",
            "FACULTY, 200",
            "HOD, 200",
            "ADMIN, 200",
            "SUPER_ADMIN, 200"})
    void tc02_recommendationsAreOpenToEveryRole(String role, int expected) throws Exception {
        mvc.perform(as(role, get("/api/recommendations/jobs/1/syllabus/2"))).andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "TC-02 {0} on /api/student/roadmap -> {1}")
    @CsvSource({
            "FACULTY, 403",
            "HOD, 403",
            "STUDENT, 200",
            "ADMIN, 200",
            "SUPER_ADMIN, 200"})
    void tc02_roadmapIsForStudentsAndAdmins(String role, int expected) throws Exception {
        mvc.perform(as(role, get("/api/student/roadmap").param("skill", "Docker"))).andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "{0} on /api/admin/** -> {1}")
    @CsvSource({
            "STUDENT, 403",
            "FACULTY, 403",
            "HOD, 200",
            "ADMIN, 200"})
    void adminAreaIsForManagers(String role, int expected) throws Exception {
        mvc.perform(as(role, get("/api/admin/users"))).andExpect(status().is(expected));
    }

    @Test
    void notSignedInGets401AndSignInIsOpen() throws Exception {
        mvc.perform(get("/api/resume/matches")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recommendations/jobs/1/syllabus/2")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/roadmap")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void otherStudentPagesStayOpenToAnySignedInUser() throws Exception {
        mvc.perform(as("FACULTY", get("/api/student/skills"))).andExpect(status().isOk());
    }

    @Test
    void mustChangePasswordBlocksEverythingButAuth() throws Exception {
        AppUser fresh = new AppUser();
        fresh.setId(2L);
        fresh.setRole("STUDENT");
        fresh.setMustChangePassword(true);
        doReturn(Optional.of(fresh)).when(users).findByEmail(anyString());
        mvc.perform(as("STUDENT", get("/api/resume/matches"))).andExpect(status().isForbidden());
        mvc.perform(as("STUDENT", post("/api/auth/change-password"))).andExpect(status().isOk());
    }
}
