package com.omni.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param jwtSecret   HMAC secret shared with the Identity Service (at least 32 bytes)
 * @param publicPaths entries of the form {@code METHOD:/ant/path/**} or {@code /ant/path/**} (any method)
 */
@ConfigurationProperties(prefix = "gateway.security")
public record SecurityProperties(String jwtSecret, List<String> publicPaths) {
}
