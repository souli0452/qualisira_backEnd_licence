package com.qualiapproche.licences.config;

import com.qualiapproche.licences.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connexion par Keycloak — le mode de fonctionnement normal.
 *
 * <p>Cet outil détient la clé qui signe les licences : y accéder, c'est pouvoir s'en émettre une.
 * Il ne suffit donc pas d'être authentifié dans Keycloak, encore faut-il porter le rôle réservé
 * ({@code licences.auth.role-requis}) — sans quoi tout salarié disposant d'un compte pourrait
 * ouvrir des modules à qui il veut.</p>
 *
 * <p>Le flot est celui du code d'autorisation, avec session : le back-office étant servi par cette
 * application, la session la porte de bout en bout et le JavaScript n'a rien à connaître de
 * l'authentification.</p>
 *
 * <p><b>Les droits fins restent définis ici.</b> Keycloak dit qui est l'utilisateur et quels rôles
 * il porte ; la table {@code roles} dit ce que ces rôles ouvrent. Un compte du royaume portant
 * {@code SUPER_ADMIN} reçoit donc les permissions du rôle {@code SUPER_ADMIN} de cette base — les
 * mêmes que celles d'un compte local. Décrire les droits action par action dans Keycloak aurait
 * obligé à y recopier, et à y maintenir, un catalogue qui appartient à cette application.</p>
 */
@Configuration
@ConditionalOnProperty(name = "licences.auth.mode", havingValue = "keycloak")
@RequiredArgsConstructor
@Slf4j
public class SecuriteKeycloakConfig {

    private final RoleRepository roles;

    /**
     * Rôle exigé pour entrer.
     *
     * <p>Laissé vide, tout compte du royaume est admis — commode pour une première mise en route,
     * à ne pas laisser ainsi : la liste des partenaires et leurs conditions commerciales sont ici.
     * Chaque action reste de toute façon fermée à qui n'en a pas la permission.</p>
     */
    @Value("${licences.auth.role-requis:LICENCES_EDITEUR}")
    private String roleRequis;

    @Bean
    public SecurityFilterChain chaineKeycloak(
            HttpSecurity http,
            // Nommé explicitement : Spring MVC expose lui aussi une source CORS
            // (mvcHandlerMappingIntrospector), et l'injection par type resterait ambiguë.
            @Qualifier("sourceCors") CorsConfigurationSource cors,
            JetonAntiRejeuFilter jetonAntiRejeu,
            ClientRegistrationRepository royaume) throws Exception {
        http
                .cors(c -> c.configurationSource(cors))
                // Session portée par un cookie : une requête d'écriture doit prouver qu'elle vient
                // bien du back-office. Le jeton est déposé dans un cookie lisible, que le front
                // renvoie en en-tête.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(jetonAntiRejeu, CsrfFilter.class)
                .authorizeHttpRequests(requetes -> {
                    // L'écran de connexion doit pouvoir demander comment on se connecte avant de
                    // l'être : sans cela, il n'a aucun moyen de savoir qu'il faut un bouton
                    // Keycloak plutôt qu'un formulaire.
                    requetes.requestMatchers("/api/session/mode").permitAll();
                    if (roleRequis == null || roleRequis.isBlank()) {
                        requetes.requestMatchers("/api/**").authenticated();
                    } else {
                        requetes.requestMatchers("/api/**").hasAuthority("ROLE_" + roleRequis);
                    }
                    // Le back-office lui-même est servi sans session : ce sont des fichiers, sans
                    // donnée. Le fermer enverrait l'utilisateur vers Keycloak avant même que
                    // l'écran ait pu s'afficher, et son bouton de connexion ne servirait jamais.
                    // Ce qui protège est derrière, sur chaque appel d'API.
                    requetes.anyRequest().permitAll();
                })
                .oauth2Login(connexion -> connexion
                        .userInfoEndpoint(infos -> infos.oidcUserService(serviceUtilisateur())))
                .exceptionHandling(erreurs -> erreurs
                        // Un appel de l'écran reçoit un 401 nu, jamais la redirection vers
                        // Keycloak : le navigateur la suivrait au sein d'un XHR et rapporterait la
                        // page de connexion du royaume, que rien ne sait lire. C'est l'écran qui
                        // doit décider d'y envoyer la fenêtre entière.
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                        // Authentifié mais sans le rôle d'entrée : le renvoyer vers Keycloak
                        // boucherait — il s'y reconnecterait aussitôt pour se voir refuser encore.
                        // L'écran de connexion lui dit ce qui manque, et à qui le demander.
                        .accessDeniedHandler(refusDAcces()))
                .logout(deconnexion -> deconnexion
                        // La déconnexion doit valoir dans le royaume, pas seulement ici : sans
                        // cela, le cookie de Keycloak rouvrirait la session au clic suivant, et
                        // « se déconnecter » ne déconnecterait de rien sur un poste partagé.
                        .logoutSuccessHandler(deconnexionDuRoyaume(royaume))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("LICENCES_SESSION", "JSESSIONID"));

        log.info("Authentification par Keycloak activée{}", (roleRequis == null || roleRequis.isBlank())
                ? " (aucun rôle exigé — à restreindre)" : " ; rôle exigé : " + roleRequis);
        return http.build();
    }

    /**
     * Ferme la session du royaume en même temps que celle-ci.
     *
     * <p>Keycloak ramène ensuite sur l'écran de connexion. L'adresse de retour est déduite de la
     * requête plutôt qu'écrite en dur : en développement, le front est servi sur un autre port que
     * l'application, et une adresse fixe renverrait sur le mauvais.</p>
     */
    private LogoutSuccessHandler deconnexionDuRoyaume(ClientRegistrationRepository royaume) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(royaume);
        handler.setPostLogoutRedirectUri("{baseUrl}/connexion");
        return handler;
    }

    /**
     * Le refus d'accès, dit à qui peut le comprendre.
     *
     * <p>Un appel de l'écran reçoit un 403 et son message ; une navigation revient sur l'écran de
     * connexion, qui explique quel rôle manque. Servir la page d'erreur du serveur laisserait
     * l'utilisateur devant une trace technique sans savoir à qui s'adresser.</p>
     */
    private AccessDeniedHandler refusDAcces() {
        return (requete, reponse, refus) -> {
            String chemin = requete.getRequestURI();
            log.warn("Accès refusé à {} sur {} — le rôle {} manque.",
                    requete.getUserPrincipal() != null ? requete.getUserPrincipal().getName() : "?",
                    chemin, roleRequis);
            if (chemin.startsWith("/api/")) {
                reponse.setStatus(HttpStatus.FORBIDDEN.value());
                reponse.setContentType("application/json;charset=UTF-8");
                reponse.getWriter().write("{\"message\":\"Votre compte n'a pas le rôle "
                        + roleRequis + ", exigé pour entrer.\"}");
                return;
            }
            reponse.sendRedirect(requete.getContextPath() + "/connexion?acces=refuse");
        };
    }

    /**
     * Traduit les rôles Keycloak en habilitations Spring, puis en permissions.
     *
     * <p>Keycloak place les rôles du royaume dans {@code realm_access.roles}, et ceux du client
     * dans {@code resource_access.<client>.roles} — deux emplacements que Spring ne lit pas de
     * lui-même. Sans cette traduction, l'utilisateur arrive avec la seule habilitation
     * {@code OIDC_USER} et le contrôle de rôle fermerait la porte à tout le monde.</p>
     *
     * <p>Chaque rôle connu de la table {@code roles} apporte en outre ses permissions : c'est
     * elles que contrôlent les {@code @PreAuthorize}. Un rôle du royaume sans équivalent ici
     * n'ouvre rien de plus — il n'est pas refusé pour autant, d'autres applications du royaume
     * peuvent s'en servir.</p>
     */
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> serviceUtilisateur() {
        OidcUserService delegue = new OidcUserService();
        return requete -> {
            OidcUser utilisateur = delegue.loadUser(requete);
            Set<GrantedAuthority> habilitations = new LinkedHashSet<>(utilisateur.getAuthorities());

            Set<String> codes = new LinkedHashSet<>();
            relever(codes, utilisateur.getClaimAsMap("realm_access"));
            Map<String, Object> parClient = utilisateur.getClaimAsMap("resource_access");
            if (parClient != null) {
                parClient.values().forEach(valeur -> {
                    if (valeur instanceof Map<?, ?> client) {
                        relever(codes, client);
                    }
                });
            }

            codes.forEach(code -> {
                habilitations.add(new SimpleGrantedAuthority("ROLE_" + code));
                roles.findByCodeIgnoreCase(code).ifPresent(role -> role.codesDesPermissions()
                        .forEach(permission ->
                                habilitations.add(new SimpleGrantedAuthority(permission))));
            });

            log.debug("Connexion Keycloak de {} — rôles {}, {} habilitation(s)",
                    utilisateur.getClaimAsString("preferred_username"), codes, habilitations.size());

            // « preferred_username » plutôt que « sub » : c'est ce nom qui est inscrit dans
            // « émise par » sur chaque licence, et un identifiant technique n'y apprendrait rien.
            return new DefaultOidcUser(habilitations, utilisateur.getIdToken(),
                    utilisateur.getUserInfo(), "preferred_username");
        };
    }

    private void relever(Set<String> codes, Map<?, ?> source) {
        if (source == null || !(source.get("roles") instanceof List<?> lus)) {
            return;
        }
        lus.forEach(role -> codes.add(String.valueOf(role)));
    }
}
