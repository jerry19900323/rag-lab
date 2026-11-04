package com.example.raglab.infrastructure.auth;

import com.example.raglab.application.AuthSessionService;
import com.example.raglab.application.UserAccountStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers authentication at the web boundary for the complete application context. */
@Configuration
public class AuthWebConfiguration {

    @Bean
    BearerAuthenticationFilter bearerAuthenticationFilter(
            AuthSessionService sessionService,
            UserAccountStore userAccountStore) {
        return new BearerAuthenticationFilter(sessionService, userAccountStore);
    }
}
