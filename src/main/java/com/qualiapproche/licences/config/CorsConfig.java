package com.qualiapproche.licences.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Origines admises pour le back-office.
 *
 * <p>Le back-office est une application Angular servie à part : sans cette déclaration, le
 * navigateur refuse ses appels dès que les deux ne partagent pas la même origine.</p>
 *
 * <p>En développement, le serveur Angular relaie les appels vers ce service
 * ({@code proxy.conf.json}) : tout paraît venir de la même origine et cette configuration ne
 * sert pas. Elle vaut pour le déploiement, où le back-office est publié sous son propre nom.</p>
 *
 * <p>Les origines sont énumérées, jamais {@code *} : la session accompagne chaque appel, et une
 * origine ouverte laisserait n'importe quel site émettre des licences avec la session d'un
 * administrateur connecté.</p>
 */
@Configuration
public class CorsConfig {

    @Value("${licences.cors.origines:http://localhost:4300}")
    private String origines;

    @Bean
    public CorsConfigurationSource sourceCors() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origines.split(","))
                .map(String::trim)
                .filter(origine -> !origine.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Sans quoi le navigateur n'enverrait ni la session ni le jeton anti-rejeu.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
