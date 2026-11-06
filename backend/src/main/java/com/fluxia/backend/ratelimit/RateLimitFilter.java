package com.fluxia.backend.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxia.backend.auth.AuthenticatedUser;
import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.shared.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Frena dos abusos: probar contraseñas y gastar la cuota de la IA.
 *
 * <p>En el ingreso solo cuentan los intentos fallidos, y por IP: quien entra
 * bien no gasta su cupo, y por correo un atacante podría bloquear la cuenta
 * de otra persona equivocándose a propósito. El bloqueo ocurre antes de
 * comparar la contraseña, porque BCrypt es lento a propósito y repetirlo sin
 * límite también sería un ataque.
 *
 * <p>En el chat cuenta cada pregunta, por usuario: la IA tiene una cuota
 * compartida y una sola persona no debería poder agotarla.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String CHAT_PATH = "/api/v1/ai/chat";

    private final RateLimiter limiter;
    private final FluxProperties.RateLimit limits;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(FluxProperties properties, ObjectMapper objectMapper) {
        this.limiter = new RateLimiter(Clock.systemUTC());
        this.limits = properties.getRateLimit();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (LOGIN_PATH.equals(path)) {
            filterLogin(request, response, filterChain);
        } else if (CHAT_PATH.equals(path)) {
            filterChat(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void filterLogin(HttpServletRequest request,
                             HttpServletResponse response,
                             FilterChain filterChain) throws ServletException, IOException {
        String key = "login:" + request.getRemoteAddr();
        if (limiter.isExhausted(key, limits.getLoginFailures())) {
            reject(request, response, limits.getLoginWindow(),
                    "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.");
            return;
        }
        filterChain.doFilter(request, response);
        if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            limiter.record(key, limits.getLoginWindow());
        }
    }

    private void filterChat(HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            // Sin sesión no hay a quién contarle: la seguridad responde 401.
            filterChain.doFilter(request, response);
            return;
        }
        String key = "chat:" + user.id();
        if (limiter.isExhausted(key, limits.getChatRequests())) {
            reject(request, response, limits.getChatWindow(),
                    "Hiciste muchas preguntas seguidas. Espera un momento e inténtalo de nuevo.");
            return;
        }
        limiter.record(key, limits.getChatWindow());
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        Duration retryAfter,
                        String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiErrorResponse.of("TOO_MANY_REQUESTS", message, request.getRequestURI()));
    }
}
