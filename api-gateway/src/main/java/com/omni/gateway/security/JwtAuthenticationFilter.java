package com.omni.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Verifies the access token once at the edge and forwards the caller identity to downstream
 * services as trusted headers. Any identity header sent by the client is stripped first, so it
 * cannot be spoofed.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private final SecretKey key;
    private final List<PublicPath> publicPaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthenticationFilter(SecurityProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.publicPaths = props.publicPaths() == null ? List.of()
                : props.publicPaths().stream().map(PublicPath::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_EMAIL);
                    h.remove(HEADER_USER_ROLES);
                })
                .build();

        boolean isPublic = isPublic(request);
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (isPublic) {
                return chain.filter(exchange.mutate().request(request).build());
            }
            return reject(exchange.getResponse(), "Missing bearer token");
        }

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authHeader.substring(7)).getPayload();
            if (!"access".equals(claims.get("type", String.class))) {
                throw new JwtException("Not an access token");
            }
        } catch (JwtException | IllegalArgumentException e) {
            if (isPublic) {
                return chain.filter(exchange.mutate().request(request).build());
            }
            return reject(exchange.getResponse(), "Invalid or expired token");
        }

        Object rolesClaim = claims.get("roles");
        String roles = rolesClaim instanceof Collection<?> c
                ? String.join(",", c.stream().map(String::valueOf).toList()) : "";
        ServerHttpRequest authenticated = request.mutate()
                .header(HEADER_USER_ID, claims.getSubject())
                .header(HEADER_USER_EMAIL, String.valueOf(claims.get("email")))
                .header(HEADER_USER_ROLES, roles)
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private boolean isPublic(ServerHttpRequest request) {
        String path = request.getPath().value();
        String method = request.getMethod().name();
        return publicPaths.stream().anyMatch(p -> (p.method() == null || p.method().equals(method))
                && matcher.match(p.pattern(), path));
    }

    private Mono<Void> reject(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\",\"timestamp\":\"" + Instant.now() + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Before route filters such as the rate limiter, which keys on X-User-Id.
        return -100;
    }

    record PublicPath(String method, String pattern) {
        static PublicPath parse(String raw) {
            int idx = raw.indexOf(':');
            return idx > 0 ? new PublicPath(raw.substring(0, idx).trim().toUpperCase(), raw.substring(idx + 1).trim())
                    : new PublicPath(null, raw.trim());
        }
    }
}
