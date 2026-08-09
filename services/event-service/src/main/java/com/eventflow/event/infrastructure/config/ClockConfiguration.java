package com.eventflow.event.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expose l'horloge comme une dépendance injectable.
 *
 * <p>Le domaine reçoit une {@link Clock} au lieu d'appeler {@code Instant.now()} :
 * les règles temporelles deviennent testables de façon déterministe avec
 * {@code Clock.fixed(...)}, sans manipuler d'horloge système.
 */
@Configuration
class ClockConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
