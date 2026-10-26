package com.omni.identity.service;

import com.omni.identity.config.IdentityProperties;
import com.omni.identity.domain.Role;
import com.omni.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final JwtService jwt = new JwtService(new IdentityProperties(
            new IdentityProperties.Jwt(SECRET, Duration.ofMinutes(15), Duration.ofDays(7)), null));

    @Test
    void accessTokenCarriesIdentityForTheGateway() {
        User user = new User();
        user.setId(42L);
        user.setEmail("a@b.c");
        user.setRoles(Set.of(Role.CUSTOMER, Role.STAFF));

        Claims claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(jwt.issueAccessToken(user)).getPayload();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email")).isEqualTo("a@b.c");
        assertThat(claims.get("type")).isEqualTo("access");
        assertThat(claims.get("roles", List.class)).containsExactly("CUSTOMER", "STAFF");
    }

    @Test
    void refreshTokensAreRandomAndHashedDeterministically() {
        String token = jwt.newRefreshToken();
        assertThat(token).isNotEqualTo(jwt.newRefreshToken());
        assertThat(jwt.hash(token)).hasSize(64).isEqualTo(jwt.hash(token));
    }
}
