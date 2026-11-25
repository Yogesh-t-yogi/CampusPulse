package com.campuspulse.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test helper: the shipped default of a setting in src/main/resources/application.properties,
 * with {@code ${ENV_VAR:default}} placeholders resolved to their default. Lets unit tests use the
 * real configured values (rate limits, thresholds) without starting Spring or a database.
 */
public final class AppDefaults {

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{[^:}]+:(.*)}$");
    private static final Properties PROPERTIES = load();

    private AppDefaults() {
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = AppDefaults.class.getResourceAsStream("/application.properties")) {
            if (in == null) throw new IllegalStateException("application.properties not on the test classpath");
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    public static String get(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) throw new IllegalArgumentException("No property " + key + " in application.properties");
        value = value.trim();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.matches()) {
            value = m.group(1).trim();
            m = PLACEHOLDER.matcher(value);
        }
        return value;
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static double getDouble(String key) {
        return Double.parseDouble(get(key));
    }
}
