package com.example.raglab.api;

import com.example.raglab.application.AuthenticationService;
import com.example.raglab.application.CaptchaChallenge;
import com.example.raglab.application.CaptchaService;
import com.example.raglab.application.LoginResult;
import com.example.raglab.application.AuthSessionService;
import com.example.raglab.application.AuthUser;
import com.example.raglab.infrastructure.auth.BearerAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exposes image captcha and account login operations. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CaptchaService captchaService;
    private final AuthenticationService authenticationService;
    private final AuthSessionService sessionService;

    public AuthController(
            CaptchaService captchaService,
            AuthenticationService authenticationService,
            AuthSessionService sessionService) {
        this.captchaService = captchaService;
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
    }

    @GetMapping("/captcha")
    public CaptchaChallenge captcha() {
        return captchaService.createChallenge();
    }

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(
                request.username(), request.password(), request.captchaId(), request.captchaCode());
    }

    @GetMapping("/me")
    public LoginResult.UserProfile currentUser(HttpServletRequest request) {
        AuthUser user = (AuthUser) request.getAttribute(BearerAuthenticationFilter.USER_ATTRIBUTE);
        return new LoginResult.UserProfile(user.userId(), user.username(), user.displayName(), user.role());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        sessionService.delete((String) request.getAttribute(BearerAuthenticationFilter.TOKEN_ATTRIBUTE));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String captchaId,
            @NotBlank String captchaCode) {
    }
}
