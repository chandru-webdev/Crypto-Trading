package com.cryptosim.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {
    private String from;
    /** When false, EmailService logs the link instead of sending — useful for local dev. */
    private boolean enabled;
}
