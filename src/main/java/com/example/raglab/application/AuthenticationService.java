package com.example.raglab.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Validates one-time captcha and account credentials, then creates a login session. */
@Service
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5s0F6V6DqM5lYj8xQpR8p7gR3qH3j6C";

    private final CaptchaService captchaService;
    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService sessionService;

    public AuthenticationService(
            CaptchaService captchaService,
            UserAccountStore userAccountStore,
            PasswordEncoder passwordEncoder,
            AuthSessionService sessionService) {
        this.captchaService = captchaService;
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    public LoginResult login(String username, String password, String captchaId, String captchaCode) {
        captchaService.validateAndConsume(captchaId, captchaCode);
        AuthUser user = userAccountStore.findByUsername(username == null ? "" : username.trim())
                .orElse(null);
        String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
        boolean passwordMatches = password != null && passwordEncoder.matches(password, passwordHash);
        if (user == null || !user.isActive() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        AuthSessionService.Session session = sessionService.create(user);
        return new LoginResult(
                session.token(),
                "Bearer",
                session.expiresInSeconds(),
                new LoginResult.UserProfile(user.userId(), user.username(), user.displayName(), user.role()));
    }
}
