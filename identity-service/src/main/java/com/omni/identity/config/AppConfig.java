package com.omni.identity.config;

import com.omni.identity.domain.Role;
import com.omni.identity.domain.User;
import com.omni.identity.repository.UserRepository;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Identity & User Service").version("v1"))
                .addServersItem(new Server().url("/"));
    }

    /** Creates the first administrator so the system can be operated right after deployment. */
    @Bean
    public ApplicationRunner bootstrapAdmin(UserRepository users, PasswordEncoder encoder, IdentityProperties props) {
        return args -> {
            String email = props.bootstrapAdmin().email();
            if (email == null || email.isBlank() || users.existsByEmailIgnoreCase(email)) {
                return;
            }
            User admin = new User();
            admin.setEmail(email.toLowerCase());
            admin.setPasswordHash(encoder.encode(props.bootstrapAdmin().password()));
            admin.setFullName("System Administrator");
            admin.setRoles(Set.of(Role.ADMIN, Role.STAFF, Role.CUSTOMER));
            users.save(admin);
            log.info("Bootstrap admin account created: {}", email);
        };
    }
}
