package com.campuspulse.service.auth;

import java.security.SecureRandom;

/** One-time passwords for accounts created by an admin (shown once, never stored in clear). */
public final class TempPasswords {

    // No look-alike characters (0/O, 1/l/I).
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPasswords() {
    }

    public static String generate() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            password.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        password.append(SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length())));
        for (int i = 0; i < 3; i++) {
            password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        return password.toString();
    }
}
