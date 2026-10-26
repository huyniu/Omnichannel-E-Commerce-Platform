package com.omni.gateway.config;

import com.omni.gateway.security.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

@Configuration
public class RateLimitConfig {

    /**
     * Authenticated callers are limited per user, anonymous callers per client IP
     * (X-Forwarded-For set by the host Nginx takes precedence over the socket address).
     */
    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_USER_ID);
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just("ip:" + forwarded.split(",")[0].trim());
            }
            String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(InetSocketAddress::getAddress).map(a -> a.getHostAddress()).orElse("unknown");
            return Mono.just("ip:" + ip);
        };
    }
}
