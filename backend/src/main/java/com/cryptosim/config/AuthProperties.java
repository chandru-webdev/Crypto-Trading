package com.cryptosim.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.auth")
@Getter
@Setter
public class AuthProperties {
    private long emailVerificationTtlMinutes;
    private long passwordResetTtlMinutes;
    private String verificationBaseUrl;
    private String passwordResetBaseUrl;
}
