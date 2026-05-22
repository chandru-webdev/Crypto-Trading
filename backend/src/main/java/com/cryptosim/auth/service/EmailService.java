package com.cryptosim.auth.service;

import com.cryptosim.config.AuthProperties;
import com.cryptosim.config.MailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (verification + password reset).
 * If app.mail.enabled = false (default in dev), the link is logged instead.
 * This lets developers complete the flow locally without configuring SMTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProps;
    private final AuthProperties authProps;

    public void sendVerificationEmail(String to, String token) {
        String link = authProps.getVerificationBaseUrl() + "?token=" + token;
        String subject = "Verify your Crypto Simulator account";
        String body = "Welcome to Crypto Trading Simulator!\n\n"
                + "Click the link below to verify your email (valid for "
                + authProps.getEmailVerificationTtlMinutes() + " minutes):\n\n"
                + link + "\n\n"
                + "If you didn't sign up, ignore this email.";
        deliver(to, subject, body, "verification");
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = authProps.getPasswordResetBaseUrl() + "?token=" + token;
        String subject = "Reset your Crypto Simulator password";
        String body = "We received a request to reset your password.\n\n"
                + "Click the link below to set a new password (valid for "
                + authProps.getPasswordResetTtlMinutes() + " minutes, single-use):\n\n"
                + link + "\n\n"
                + "If you didn't request this, you can safely ignore this email.";
        deliver(to, subject, body, "password reset");
    }

    private void deliver(String to, String subject, String body, String kind) {
        if (!mailProps.isEnabled()) {
            // Dev mode: log so we can grab the link without configuring SMTP.
            log.info("[MAIL DISABLED] {} email for {} -> {}", kind, to, body);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailProps.getFrom());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
