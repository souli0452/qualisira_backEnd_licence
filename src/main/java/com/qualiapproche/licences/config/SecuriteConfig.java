package com.qualiapproche.licences.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Accès sans Keycloak : les comptes du back-office, tenus en base.
 *
 * <p>Ce n'est pas le mode de fonctionnement normal — voir {@link SecuriteKeycloakConfig}, qui
 * prend le relais dès que {@code licences.auth.mode=keycloak}. Il existe parce que cet outil doit
 * pouvoir servir même quand le serveur d'identité n'est pas joignable : une licence à émettre pour
 * un client bloqué ne peut pas attendre le rétablissement de Keycloak.</p>
 *
 * <p>Les comptes ne sont plus déclarés dans la configuration mais dans la table
 * {@code utilisateurs} : c'est le super administrateur créé au démarrage qui ouvre ceux des
 * autres, et leur attribue des rôles. Un compte unique partagé ne permettait ni de savoir qui
 * avait émis quelle licence — tout le monde s'appelait « admin » —, ni d'ouvrir l'outil à un
 * commercial sans lui donner la main sur les clés.</p>
 *
 * <p>Qui a le droit de quoi ne se lit pas ici mais sur chaque méthode de contrôleur, en
 * {@code @PreAuthorize} : la chaîne se contente d'exiger une session ouverte.</p>
 *
 * <p>Un appel non authentifié reçoit un 401 <b>sans en-tête {@code WWW-Authenticate}</b> : sans
 * cela, le navigateur ouvrirait sa propre fenêtre de connexion par-dessus l'application.</p>
 */
@Configuration
@ConditionalOnProperty(name = "licences.auth.mode", havingValue = "local", matchIfMissing = true)
@Slf4j
public class SecuriteConfig {

    @Value("${licences.admin.utilisateur:admin}")
    private String utilisateur;

    @Bean
    public SecurityFilterChain chaine(
            HttpSecurity http,
            // Nommé explicitement : Spring MVC expose lui aussi une source CORS
            // (mvcHandlerMappingIntrospector), et l'injection par type resterait ambiguë.
            @Qualifier("sourceCors") CorsConfigurationSource cors,
            JetonAntiRejeuFilter jetonAntiRejeu) throws Exception {

        log.warn("Authentification locale (comptes en base, super administrateur « {} »). "
                + "Le mode normal est Keycloak : définissez licences.auth.mode=keycloak.", utilisateur);

        return http
                .cors(c -> c.configurationSource(cors))
                // Session portée par un cookie : une écriture doit prouver qu'elle vient bien du
                // back-office. Le jeton est déposé dans un cookie lisible, que le front renvoie
                // en en-tête. La connexion en est dispensée : aucune session n'existe encore.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/session/connexion"))
                .addFilterAfter(jetonAntiRejeu, CsrfFilter.class)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(requetes -> requetes
                        // « mode » avec « connexion » : l'écran demande d'abord comment on se
                        // connecte ici, avant de présenter un formulaire ou un bouton Keycloak.
                        .requestMatchers("/api/session/connexion", "/api/session/mode").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Le back-office lui-même est servi sans session : c'est un ensemble de
                        // fichiers, sans donnée. Exiger une session pour le charger renverrait un
                        // 401 sur index.html, et l'écran de connexion ne s'afficherait jamais —
                        // on ne peut pas demander d'être connecté pour atteindre de quoi se
                        // connecter. Ce qui protège est derrière, sur chaque appel d'API.
                        .anyRequest().permitAll())
                // 401 nu : le navigateur ne doit pas ouvrir sa fenêtre d'authentification, c'est
                // l'écran de connexion du back-office qui prend la main.
                .exceptionHandling(erreurs -> erreurs
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /**
     * Vérifie les identifiants présentés à la connexion.
     *
     * <p>Les comptes viennent de {@code ComptesDuBackOffice}, adossé à la table des utilisateurs :
     * un compte suspendu s'y voit refusé sans qu'aucun contrôle supplémentaire soit nécessaire.</p>
     */
    @Bean
    public AuthenticationManager gestionnaireDAuthentification(UserDetailsService comptes,
                                                               PasswordEncoder encodeur) {
        DaoAuthenticationProvider fournisseur = new DaoAuthenticationProvider();
        fournisseur.setUserDetailsService(comptes);
        fournisseur.setPasswordEncoder(encodeur);
        return new ProviderManager(fournisseur);
    }
}
