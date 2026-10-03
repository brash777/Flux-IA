package com.fluxia.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxia.backend.shared.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el encabezado {@code Authorization: Bearer <token>} y, si el
 * token es valido, deja al usuario autenticado para el resto de la
 * peticion.
 *
 * <p>Si no hay encabezado, el filtro no hace nada: sera la
 * configuracion de seguridad la que decida si esa ruta necesitaba
 * autenticacion. Si hay encabezado pero el token no sirve, se responde
 * aqui mismo distinguiendo <b>vencido</b> de <b>invalido</b>: el
 * cliente necesita saber si le toca refrescar el token o volver a
 * pedir la contrasena.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            AuthenticatedUser user = jwtService.verify(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtService.TokenExpiredException ex) {
            reject(request, response, "TOKEN_EXPIRED",
                    "Tu sesion expiro. Refresca el token para continuar.");
            return;
        } catch (JwtService.TokenInvalidException ex) {
            reject(request, response, "TOKEN_INVALID",
                    "El token de acceso no es valido.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        String code,
                        String message) throws IOException {

        // Si el token no sirve, no queda ninguna identidad a medio armar.
        SecurityContextHolder.clearContext();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.of(code, message, request.getRequestURI()));
    }
}
