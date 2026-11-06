package com.fluxia.backend.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fluxia.backend.auth.AuthenticatedUser;
import com.fluxia.backend.config.FluxProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String CHAT = "/api/v1/ai/chat";

    private RateLimitFilter filter;
    private AtomicInteger reachedApp;

    @BeforeEach
    void setUp() {
        FluxProperties properties = new FluxProperties();
        properties.getRateLimit().setLoginFailures(2);
        properties.getRateLimit().setChatRequests(2);
        filter = new RateLimitFilter(properties, new ObjectMapper().registerModule(new JavaTimeModule()));
        reachedApp = new AtomicInteger();
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Tras los intentos fallidos permitidos responde 429 sin llegar a comparar la contraseña")
    void blocksLoginAfterFailures() throws Exception {
        assertThat(post(LOGIN, "10.0.0.1", 401).getStatus()).isEqualTo(401);
        assertThat(post(LOGIN, "10.0.0.1", 401).getStatus()).isEqualTo(401);

        MockHttpServletResponse blocked = post(LOGIN, "10.0.0.1", 200);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Demasiados intentos");
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(reachedApp).hasValue(2);
    }

    @Test
    @DisplayName("Un ingreso correcto no gasta el cupo de intentos")
    void successfulLoginsAreNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post(LOGIN, "10.0.0.1", 200).getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Los fallos de una IP no bloquean a otra")
    void loginLimitIsPerAddress() throws Exception {
        post(LOGIN, "10.0.0.1", 401);
        post(LOGIN, "10.0.0.1", 401);

        assertThat(post(LOGIN, "10.0.0.2", 200).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("En el chat, cada usuario tiene su propio cupo de preguntas")
    void chatLimitIsPerUser() throws Exception {
        signIn(UUID.randomUUID());
        assertThat(post(CHAT, "10.0.0.1", 200).getStatus()).isEqualTo(200);
        assertThat(post(CHAT, "10.0.0.1", 200).getStatus()).isEqualTo(200);
        assertThat(post(CHAT, "10.0.0.1", 200).getStatus()).isEqualTo(429);

        signIn(UUID.randomUUID());
        assertThat(post(CHAT, "10.0.0.1", 200).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sin sesión, el chat no cuenta nada: responde la seguridad")
    void chatWithoutSessionIsNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post(CHAT, "10.0.0.1", 200).getStatus()).isEqualTo(200);
        }
    }

    private void signIn(UUID id) {
        AuthenticatedUser user = new AuthenticatedUser(id, "a@b.co", "A");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private MockHttpServletResponse post(String path, String address, int appStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpServlet app = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                reachedApp.incrementAndGet();
                resp.setStatus(appStatus);
            }
        };
        filter.doFilter(request, response, new MockFilterChain(app));
        return response;
    }
}
