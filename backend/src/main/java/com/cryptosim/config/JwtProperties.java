package com.cryptosim.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {
    /** Base64-encoded HMAC key (>= 32 bytes once decoded for HS256). */
    private String secret;
    private long accessTokenTtlMinutes;
    private long refreshTokenTtlDays;
    private String issuer;
}
