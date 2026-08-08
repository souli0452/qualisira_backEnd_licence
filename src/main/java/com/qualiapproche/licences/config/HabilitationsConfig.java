package com.qualiapproche.licences.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Ce que les deux modes d'authentification partagent : le contrôle par permission, et l'empreinte
 * des mots de passe.
 *
 * <p>{@code @EnableMethodSecurity} ouvre les {@code @PreAuthorize} posés sur les contrôleurs.
 * C'est là, au plus près de l'action, que les droits sont vérifiés : une règle écrite dans la
 * chaîne de filtres se lit loin du code qu'elle protège, et un chemin ajouté sans qu'on y pense
 * s'y retrouve ouvert par défaut. Ici, une méthode sans annotation reste fermée à qui n'a pas la
 * permission — parce qu'aucune permission ne lui correspond.</p>
 *
 * <p>L'encodeur vit ici plutôt que dans l'un des deux modes : les comptes se créent et se
 * réinitialisent même quand l'authentification passe par Keycloak.</p>
 */
@Configuration
@EnableMethodSecurity
public class HabilitationsConfig {

    @Bean
    public PasswordEncoder encodeur() {
        return new BCryptPasswordEncoder();
    }
}
