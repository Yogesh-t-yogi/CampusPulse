package com.campuspulse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring can't pick between several public constructors on its own; the backend then fails to start
 * ("No default constructor found"). The full context test needs a database, so check this cheaply here.
 */
class BeanConstructorTest {

    @Test
    void beansWithSeveralConstructorsMarkOneAutowired() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class)); // also @Service, @RestController, ...
        List<String> problems = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents("com.campuspulse")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            boolean hasNoArg = Arrays.stream(constructors).anyMatch(c -> c.getParameterCount() == 0);
            boolean marked = Arrays.stream(constructors).anyMatch(c -> c.isAnnotationPresent(Autowired.class));
            if (constructors.length > 1 && !hasNoArg && !marked) problems.add(type.getName());
        }
        assertTrue(problems.isEmpty(), "Add @Autowired to one constructor of: " + problems);
    }
}
