package com.fluxia.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxia.backend.auth.JwtAuthenticationFilter;
import com.fluxia.backend.ratelimit.RateLimitFilter;
import com.fluxia.backend.shared.ApiErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuracion de seguridad de la API.
 *
 * <p>Tres decisiones que conviene poder sustentar:
 *
 * <ul>
 *   <li><b>Sin sesiones ni CSRF.</b> La API es sin estado: la identidad
 *       viaja en el token de cada peticion. CSRF protege contra el
 *       envio automatico de cookies por el navegador, y aqui no hay
 *       cookies de sesion que enviar.
 *   <li><b>Todo cerrado por defecto.</b> Solo se abren rutas una por
 *       una. Si manana se agrega un endpoint y nadie toca este archivo,
 *       nace protegido, no expuesto.
 *   <li><b>BCrypt de coste 10.</b> Es un hash lento a proposito: hace
 *       inviable probar contrasenas por fuerza bruta aunque se filtre
 *       la base de datos.
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final FluxProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          FluxProperties properties,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Por omision, Spring Security prohibe guardar en cache cualquier
                // respuesta, y el celular volvia a bajar toda la app cada vez que
                // la abria. Los datos de la API siguen sin guardarse; la cache de
                // los archivos de la app se define en application.yml.
                .headers(headers -> headers
                        .cacheControl(cache -> cache.disable())
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                request -> request.getRequestURI().startsWith("/api/"),
                                new CacheControlHeadersWriter())))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Registro, inicio de sesion y recuperacion: por
                        // definicion se piden sin token.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Documentacion navegable de la API. Solo existe con
                        // DOCS_ENABLED=true o en el perfil dev (application.yml).
                        .requestMatchers("/docs", "/docs/**", "/swagger-ui/**", "/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // La app web que entrega este mismo servidor: son
                        // archivos publicos, como si los sirviera Live
                        // Server. Solo lectura, y solo estas carpetas: el
                        // resto de frontend/ (node_modules, .md) queda cerrado.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/styleguide.html",
                                "/manifest.webmanifest", "/css/**", "/js/**", "/assets/**").permitAll()
                        // El navegador manda OPTIONS antes de cada
                        // peticion con token; no lleva credenciales.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Todo lo demas exige token valido.
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(
                                    response.getWriter(),
                                    ApiErrorResponse.of(
                                            "UNAUTHENTICATED",
                                            "Necesitas iniciar sesión para acceder a este recurso.",
                                            request.getRequestURI()));
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Despues del token, para saber de quien es cada pregunta al chat.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }

    /**
     * BCrypt directo, no el codificador delegante que trae Spring por
     * defecto. El delegante espera hashes con prefijo ({@code {bcrypt}...})
     * y los de la semilla de desarrollo no lo llevan.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Lista explicita de origenes, nunca "*": con "*" cualquier
        // sitio web podria llamar a la API desde el navegador.
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
