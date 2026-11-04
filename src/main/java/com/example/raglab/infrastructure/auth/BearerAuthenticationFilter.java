package com.example.raglab.infrastructure.auth;

import com.example.raglab.application.AuthSessionService;
import com.example.raglab.application.AuthUser;
import com.example.raglab.application.UserAccountStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Resolves Redis-backed bearer sessions for protected API requests. */
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = BearerAuthenticationFilter.class.getName() + ".user";
    public static final String TOKEN_ATTRIBUTE = BearerAuthenticationFilter.class.getName() + ".token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthSessionService sessionService;
    private final UserAccountStore userAccountStore;

    public BearerAuthenticationFilter(AuthSessionService sessionService, UserAccountStore userAccountStore) {
        this.sessionService = sessionService;
        this.userAccountStore = userAccountStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.startsWith("/api/health")
                || path.equals("/api/auth/captcha")
                || path.equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request.getHeader("Authorization"));
        AuthUser user = sessionService.findUserId(token)
                .flatMap(userAccountStore::findById)
                .filter(AuthUser::isActive)
                .orElse(null);
        if (user == null) {
            unauthorized(response);
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, user);
        request.setAttribute(TOKEN_ATTRIBUTE, token);
        filterChain.doFilter(request, response);
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Authentication is required\"}");
    }
}
