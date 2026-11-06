package com.fluxia.backend.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    private MovableClock clock;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        limiter = new RateLimiter(clock);
    }

    @Test
    @DisplayName("Permite hasta el tope y bloquea al alcanzarlo")
    void blocksWhenLimitIsReached() {
        limiter.record("a", WINDOW);
        limiter.record("a", WINDOW);
        assertThat(limiter.isExhausted("a", 3)).isFalse();

        limiter.record("a", WINDOW);
        assertThat(limiter.isExhausted("a", 3)).isTrue();
    }

    @Test
    @DisplayName("Al terminar la ventana, la cuenta vuelve a empezar")
    void resetsAfterWindow() {
        limiter.record("a", WINDOW);
        assertThat(limiter.isExhausted("a", 1)).isTrue();

        clock.advance(WINDOW);
        assertThat(limiter.isExhausted("a", 1)).isFalse();

        limiter.record("a", WINDOW);
        limiter.record("a", WINDOW);
        assertThat(limiter.isExhausted("a", 3)).isFalse();
    }

    @Test
    @DisplayName("Seguir intentando no alarga la ventana")
    void newEventsDoNotExtendWindow() {
        limiter.record("a", WINDOW);
        clock.advance(WINDOW.minusSeconds(1));
        limiter.record("a", WINDOW);
        assertThat(limiter.isExhausted("a", 2)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.isExhausted("a", 2)).isFalse();
    }

    @Test
    @DisplayName("Una clave no gasta el cupo de otra")
    void keysAreIndependent() {
        limiter.record("a", WINDOW);
        assertThat(limiter.isExhausted("a", 1)).isTrue();
        assertThat(limiter.isExhausted("b", 1)).isFalse();
    }

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T12:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
