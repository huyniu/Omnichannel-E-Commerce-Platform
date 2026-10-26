package com.omni.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(Jwt jwt, BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record BootstrapAdmin(String email, String password) {
    }
}
