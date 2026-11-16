package com.campuspulse.controller;

import com.campuspulse.service.ai.LlmService;
import com.campuspulse.service.job.SkillAliasService;
import com.campuspulse.service.student.StudentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spring Boot 4 writes HTTP responses with Jackson 3. A Jackson 2 JsonNode returned from a controller is
 * written as a bean description, so the "How to learn it" roadmap reached the page without its steps.
 */
class ResponseTypeTest {

    @Test
    void controllersNeverReturnJackson2Types() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> bad = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents("com.campuspulse")) {
            for (Method m : Class.forName(definition.getBeanClassName()).getDeclaredMethods()) {
                if (m.getGenericReturnType().getTypeName().contains("com.fasterxml.jackson")) {
                    bad.add(m.getDeclaringClass().getSimpleName() + "." + m.getName());
                }
            }
        }
        assertTrue(bad.isEmpty(), "return plain maps/DTOs instead of Jackson 2 types: " + bad);
    }

    @Test
    void roadmapReachesTheBrowserWithItsSteps() {
        LlmService llm = mock(LlmService.class);
        when(llm.generateJson(anyString())).thenReturn(
                "{\"summary\":\"Containers\",\"steps\":[{\"title\":\"Basics\",\"action\":\"Install Docker\",\"hours\":5}],\"project\":\"Todo API\"}");
        StudentService service = new StudentService(mock(JdbcTemplate.class), null, null, null, null, null,
                new SkillAliasService(), null, llm, new ObjectMapper());

        Map<String, Object> view = service.roadmapView("Docker");
        // Serialise exactly as the web layer does (Jackson 3).
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(view);

        assertTrue(json.contains("\"steps\":[{\"title\":\"Basics\""), json);
        assertTrue(json.contains("\"summary\":\"Containers\""), json);
    }
}
