package com.campuspulse.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Emails sent to people about their account (new account, password reset).
 * Works with any SMTP provider or plan (Gmail app password, Brevo, SendGrid, Amazon SES, college SMTP):
 * set MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD and MAIL_FROM. Without MAIL_HOST nothing is
 * sent and the app works as before (the HOD / admin hands out the password).
 * Mails go out in the background, so a slow mail server never slows down account creation.
 */
@Service
public class AccountEmailService {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String appUrl;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "account-mail");
        thread.setDaemon(true);
        return thread;
    });

    public AccountEmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${app.url:http://localhost:5173}") String appUrl,
            @Value("${spring.mail.host:}") String host) {
        this.mailSender = host == null || host.isBlank() ? null : mailSender;
        this.from = from == null ? "" : from.trim();
        this.appUrl = appUrl;
    }

    /** True when an SMTP server is configured. */
    public boolean enabled() {
        return mailSender != null && mailSender.getIfAvailable() != null;
    }

    /** New account, with the college name and the role in plain words. Returns true if the mail was queued. */
    public boolean sendWelcome(String to, String name, String role, String college, String password) {
        boolean hasCollege = college != null && !college.isBlank();
        String body = """
                Hello %s,

                %s has created a CampusPulse account for you.

                College: %s
                Role: %s

                Sign in at: %s
                Email: %s
                One-time password: %s

                You will be asked to choose your own password when you first sign in.
                If you did not expect this email, please tell your college admin.

                CampusPulse
                """.formatted(name,
                hasCollege ? college : "Your college",
                hasCollege ? college : "-",
                roleLabel(role), appUrl, to, password);
        String subject = hasCollege ? "Your CampusPulse account at " + college : "Your CampusPulse account";
        return send(to, subject, body);
    }

    static String roleLabel(String role) {
        if (role == null) return "CampusPulse user";
        return switch (role) {
            case "SUPER_ADMIN" -> "Platform admin";
            case "ADMIN" -> "College admin";
            case "HOD" -> "Head of department";
            case "FACULTY" -> "Faculty";
            case "STUDENT" -> "Student";
            default -> role;
        };
    }

    /** Password reset by a HOD / college admin. */
    public boolean sendPasswordReset(String to, String name, String password) {
        String body = """
                Hello %s,

                Your CampusPulse password was reset by your HOD or college admin.

                Sign in at: %s
                Email: %s
                One-time password: %s

                You will be asked to choose a new password when you sign in.
                If you did not ask for this, please tell your college admin.

                CampusPulse
                """.formatted(name, appUrl, to, password);
        return send(to, "Your CampusPulse password was reset", body);
    }

    /**
     * Sends a test email now (not in the background) so the result can be shown.
     * Returns null on success, otherwise the reason.
     */
    public String sendTestNow(String to) {
        JavaMailSender mail = mailSender == null ? null : mailSender.getIfAvailable();
        if (mail == null) {
            return "Email is not set up: set MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD and MAIL_FROM, then restart.";
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isEmpty()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject("CampusPulse test email");
        message.setText("Email from CampusPulse works. New accounts and password resets will be emailed.\n\n" + appUrl);
        try {
            mail.send(message);
            return null;
        } catch (RuntimeException e) {
            log.warn("Test email failed: {}", e.getMessage());
            return "The mail server refused: " + e.getMessage();
        }
    }

    boolean send(String to, String subject, String body) {
        JavaMailSender mail = mailSender == null ? null : mailSender.getIfAvailable();
        if (mail == null || to == null || to.isBlank()) {
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isEmpty()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.submit(() -> {
            try {
                mail.send(message);
            } catch (RuntimeException e) {
                log.warn("Could not send \"{}\" email: {}", subject, e.getMessage());
            }
        });
        return true;
    }
}
