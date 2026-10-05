package com.fluxia.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacion navegable de la API, disponible en /docs.
 *
 * <p>Declarar el esquema de seguridad aqui habilita el boton
 * "Authorize" de Swagger UI: se pega el token una vez y se pueden
 * probar todos los endpoints protegidos desde el navegador, sin
 * necesidad de Postman ni de curl.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI fluxIaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flux IA API")
                        .version("v1")
                        .description("""
                                API de finanzas personales con asistente de IA.

                                Backend compartido por la app movil, la de escritorio
                                y la del smartwatch.

                                Todas las cifras (saldo, reportes, contexto de la IA)
                                se calculan a partir de la tabla de transacciones:
                                no hay totales guardados aparte.

                                Para probar los endpoints protegidos: inicia sesion en
                                POST /api/v1/auth/login, copia el accessToken y pegalo
                                en el boton Authorize de arriba.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token de acceso devuelto por /api/v1/auth/login")));
    }
}
