package com.eventflow.booking.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration de l'accès à {@code event-service}.
 *
 * <p>Un {@code record} validé plutôt que des {@code @Value} éparpillés : la
 * configuration est typée, groupée, et une valeur manquante fait échouer le
 * démarrage au lieu d'injecter {@code null} qui explosera au premier appel.
 */
@Validated
@ConfigurationProperties(prefix = "eventflow.event-service")
record EventServiceProperties(

        @NotBlank String baseUrl,

        /** Délai d'établissement de la connexion TCP. */
        @NotNull Duration connectTimeout,

        /** Délai d'attente de la réponse une fois la requête envoyée. */
        @NotNull Duration readTimeout) {
}
