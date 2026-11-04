package com.example.raglab.infrastructure.auth;

import com.example.raglab.application.UserAccountStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Optionally creates the first administrator from deployment-only environment variables. */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminUserInitializer(
            UserAccountStore userAccountStore,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_USERNAME:admin}") String username,
            @Value("${ADMIN_PASSWORD:}") String password,
            @Value("${ADMIN_DISPLAY_NAME:Administrator}") String displayName) {
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (password == null || password.isBlank()) {
            logger.info("ADMIN_PASSWORD is not set; administrator bootstrap was skipped");
            return;
        }
        if (userAccountStore.createIfAbsent(username, passwordEncoder.encode(password), displayName)) {
            logger.info("Created bootstrap administrator account {}", username);
        }
        userAccountStore.promoteToAdmin(username);
    }
}
