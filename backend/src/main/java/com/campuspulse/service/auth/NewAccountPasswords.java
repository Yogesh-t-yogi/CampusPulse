package com.campuspulse.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * First password for accounts made by a HOD / college admin (single, Excel import, reset).
 * DEFAULT_USER_PASSWORD set (8+ characters): everyone gets that default.
 * Not set: a random one-time password per account (shown once).
 * Either way the person must choose their own password at first sign-in.
 */
@Component
public class NewAccountPasswords {

    private static final Logger log = LoggerFactory.getLogger(NewAccountPasswords.class);

    private final String defaultPassword;

    public NewAccountPasswords(@Value("${app.default-user-password:}") String defaultPassword) {
        String value = defaultPassword == null ? "" : defaultPassword.trim();
        if (!value.isEmpty() && value.length() < 8) {
            log.warn("DEFAULT_USER_PASSWORD ignored: it needs at least 8 characters. Using random passwords.");
            value = "";
        }
        this.defaultPassword = value;
    }

    public String next() {
        return defaultPassword.isEmpty() ? TempPasswords.generate() : defaultPassword;
    }

    public boolean usesDefault() {
        return !defaultPassword.isEmpty();
    }
}
