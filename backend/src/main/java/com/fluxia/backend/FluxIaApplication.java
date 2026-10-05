package com.fluxia.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del backend de Flux IA.
 *
 * <p>Este backend es deliberadamente independiente de cualquier cliente:
 * expone una API REST que consumiran por igual la app movil, la de
 * escritorio y la del smartwatch. No contiene nada especifico de una
 * de ellas.
 *
 * <p>{@code @ConfigurationPropertiesScan} hace que Spring descubra las
 * clases anotadas con {@code @ConfigurationProperties} del paquete
 * {@code config}, que es donde se tipan los ajustes de application.yml.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FluxIaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxIaApplication.class, args);
    }
}
