package com.fluxia.backend.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ajustes del servidor que entrega la app web.
 *
 * <p>Tomcat no conoce la extensión {@code .webmanifest} y la entrega como
 * descarga genérica; con su tipo real, el navegador la reconoce como el
 * manifiesto de una app instalable.
 */
@Configuration
public class WebConfig {

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> manifestMimeType() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
