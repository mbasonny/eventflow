package com.eventflow.booking.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EventServiceProperties.class)
class RestClientConfiguration {

    /**
     * Client HTTP vers {@code event-service}, avec deux délais explicites.
     *
     * <p>Un client sans timeout est la panne la plus insidieuse d'une
     * architecture distribuée : si le serveur accepte la connexion puis ne
     * répond jamais, l'appelant attend indéfiniment. Ses threads s'accumulent,
     * son pool sature, et un service parfaitement sain tombe à cause d'un voisin
     * lent. C'est l'effet domino — et le timeout est la première ligne de
     * défense, avant même le disjoncteur.
     *
     * <p>Les deux délais couvrent des pannes différentes : le premier protège de
     * l'hôte injoignable, le second du serveur qui accepte puis se tait.
     */
    @Bean
    RestClient eventServiceRestClient(EventServiceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
