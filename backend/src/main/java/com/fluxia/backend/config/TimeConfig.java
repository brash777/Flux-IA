package com.fluxia.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * El reloj de la aplicación, como bean.
 *
 * <p>Las reglas que dependen de la hora (el modo salida termina a las 8:00,
 * el tope se reinicia a medianoche) se prueban con un reloj fijo. Si cada
 * clase llamara a {@code now()} por su cuenta, una prueba no podría decir
 * «son las 23:59 del viernes».
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
