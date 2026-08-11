package com.qualiapproche.licences.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.qualiapproche.licences.model.Role;
import com.qualiapproche.licences.service.EnvoiDuCompteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Ouvre, dans le royaume, le compte par lequel on entrera la première fois.
 *
 * <p>En mode Keycloak, l'application ne tient aucun mot de passe : le premier accès se crée donc
 * dans le royaume, et non en base. Sans ce démarrage, la mise en route imposait un détour par la
 * console d'administration de Keycloak — deux rôles à créer, deux à attribuer, et un oubli qui ne
 * se voit qu'au moment où personne n'arrive à entrer.</p>
 *
 * <p><b>Ce que cela demande</b> : que le client déclaré dans {@code KC_LICENCES_CLIENT} ait un
 * <i>compte de service</i> portant {@code manage-users} et {@code manage-realm} sur le royaume, ou
 * qu'un client distinct soit désigné par {@code KC_LICENCES_ADMIN_CLIENT}. Sans ces droits, rien
 * n'est tenté deux fois : l'étape se signale dans le journal et le démarrage se poursuit.</p>
 *
 * <p><b>Ce que cela ne fait jamais</b> : toucher à un royaume qui a déjà quelqu'un. Si un compte
 * porte déjà le rôle d'entrée, l'étape s'arrête là — un service qui réécrirait des comptes
 * existants à chaque démarrage serait un danger, pas une commodité.</p>
 *
 * <p><b>Le droit accordé ici mérite d'être pesé</b> : un client autorisé à créer des comptes dans
 * le royaume peut s'en créer un. Sur un royaume dédié, la portée reste celle de cet outil ; sur un
 * royaume partagé, elle s'étend à tout ce qu'il sert. Qui préfère ne pas l'accorder pose
 * {@code LICENCES_ADMIN_PROVISION=false} et crée le premier compte à la main.</p>
 */
@Component
@ConditionalOnProperty(name = "licences.auth.mode", havingValue = "keycloak")
@RequiredArgsConstructor
@Order(1)
@Slf4j
public class PremierCompteKeycloak implements CommandLineRunner {

    private final EnvoiDuCompteService envoiDuCompte;

    @Value("${licences.admin.provisionner:true}")
    private boolean provisionner;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:}")
    private String royaumeUri;

    /**
     * Le client d'administration, s'il en existe un.
     *
     * <p>Renseigné vide plutôt qu'absent — le défaut d'un {@code @Value} imbriqué ne jouerait pas,
     * une chaîne vide restant une valeur. Le repli sur le client de connexion se décide donc ici,
     * en {@link #ouOuvrirLaSession()}.</p>
     */
    @Value("${licences.admin.keycloak.client:}")
    private String clientDAdministration;

    @Value("${licences.admin.keycloak.secret:}")
    private String secretDAdministration;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:}")
    private String clientDeConnexion;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret:}")
    private String secretDeConnexion;

    /** Le client retenu et son secret, une fois le repli tranché. */
    private String client;
    private String secretDuClient;

    @Value("${licences.auth.role-requis:LICENCES_EDITEUR}")
    private String roleDEntree;

    @Value("${licences.admin.utilisateur:admin}")
    private String identifiant;

    @Value("${licences.admin.mot-de-passe:}")
    private String motDePasseChoisi;

    @Value("${licences.admin.nom:Super administrateur}")
    private String nom;

    @Value("${licences.admin.email:}")
    private String email;

    private final RestClient http = RestClient.create();

    @Override
    public void run(String... args) {
        // Aucune de ces conditions n'est une erreur : elles décrivent une installation qui gère
        // ses comptes elle-même. Le démarrage n'a pas à s'en émouvoir.
        if (!provisionner) {
            log.info("Premier compte du royaume : non demandé (licences.admin.provisionner=false).");
            return;
        }
        if (email == null || email.isBlank()) {
            log.warn("Premier compte du royaume : LICENCES_ADMIN_EMAIL n'est pas renseigné — "
                    + "aucun compte n'est ouvert, personne ne pourrait recevoir ses accès.");
            return;
        }
        ouOuvrirLaSession();
        if (royaumeUri == null || royaumeUri.isBlank() || secretDuClient.isBlank()
                || client.isBlank()) {
            log.warn("Premier compte du royaume : KC_LICENCES_ISSUER ou le secret du client "
                    + "manque — étape passée.");
            return;
        }

        try {
            ouvrirLePremierCompte();
        } catch (Exception e) {
            // Jamais fatal : le service doit démarrer même si Keycloak est injoignable ou si le
            // compte de service n'a pas les droits. Le message dit quoi faire à la main.
            log.warn("""

                    ════════════════════════════════════════════════════════════════════
                      Premier compte du royaume non ouvert : {}
                      Créez-le depuis la console Keycloak : un utilisateur portant les
                      rôles {} (entrée) et {} (droits), puis « Credential Reset ».
                    ════════════════════════════════════════════════════════════════════
                    """, e.getMessage(), roleDEntree, Role.SUPER_ADMIN);
        }
    }

    /**
     * Choisit le client qui ouvrira la session d'administration.
     *
     * <p>Celui qui est désigné pour cela, à défaut celui de la connexion. Un client d'administration
     * séparé évite de donner à celui que rencontrent les utilisateurs le droit de créer des
     * comptes.</p>
     */
    private void ouOuvrirLaSession() {
        boolean designe = clientDAdministration != null && !clientDAdministration.isBlank();
        client = designe ? clientDAdministration : nonNul(clientDeConnexion);
        secretDuClient = designe ? nonNul(secretDAdministration) : nonNul(secretDeConnexion);
    }

    private String nonNul(String valeur) {
        return valeur == null ? "" : valeur;
    }

    // ------------------------------------------------------------------ le déroulé

    private void ouvrirLePremierCompte() {
        String base = sansBarreFinale(royaumeUri);
        int coupure = base.lastIndexOf("/realms/");
        if (coupure < 0) {
            throw new IllegalStateException(
                    "KC_LICENCES_ISSUER ne ressemble pas à une adresse de royaume : " + base);
        }
        String serveur = base.substring(0, coupure);
        String royaume = base.substring(coupure + "/realms/".length());
        String administration = serveur + "/admin/realms/" + royaume;

        String jeton = jetonDAdministration(base);

        // Quelqu'un peut-il déjà entrer ? Alors ce royaume est en service, et il ne nous
        // appartient pas d'y ajouter un compte que personne n'a demandé.
        if (roleDejaPorte(administration, jeton)) {
            log.info("Premier compte du royaume : un compte porte déjà « {} », rien à ouvrir.",
                    roleDEntree);
            return;
        }

        creerLeRoleSiAbsent(administration, jeton, roleDEntree,
                "Ouvre l'accès au back-office des licences. N'accorde aucune permission.");
        creerLeRoleSiAbsent(administration, jeton, Role.SUPER_ADMIN,
                "Gère les comptes, les rôles et tout le reste dans le back-office des licences.");

        String utilisateurId = utilisateurExistant(administration, jeton);
        boolean nouveau = utilisateurId == null;
        String motDePasse = motDePasseChoisi == null || motDePasseChoisi.isBlank()
                ? HabilitationsInitiales.tirerUnMotDePasse()
                : motDePasseChoisi;

        if (nouveau) {
            utilisateurId = creerLUtilisateur(administration, jeton);
            poserLeMotDePasse(administration, jeton, utilisateurId, motDePasse);
        }

        attribuer(administration, jeton, utilisateurId, roleDEntree, Role.SUPER_ADMIN);

        if (!nouveau) {
            // Le compte préexistait : son mot de passe appartient à son porteur, et le remplacer
            // sans qu'il l'ait demandé le mettrait dehors. Seuls les rôles lui sont rendus.
            log.warn("Le compte « {} » existait dans le royaume sans les rôles voulus : « {} » et "
                    + "« {} » lui ont été attribués. Son mot de passe n'a pas été touché.",
                    identifiant, roleDEntree, Role.SUPER_ADMIN);
            return;
        }

        remettreLesAcces(motDePasse);
    }

    /** Le mot de passe part par courriel ; à défaut, il s'annonce dans le journal. */
    private void remettreLesAcces(String motDePasse) {
        try {
            envoiDuCompte.envoyer(email, identifiant, motDePasse, true);
            log.warn("""

                    ════════════════════════════════════════════════════════════════════
                      Compte « {} » ouvert dans le royaume, avec les rôles {} et {}.
                      Mot de passe : envoyé à {}, et volontairement absent de ce journal.
                      Keycloak en réclamera le changement à la première connexion.
                    ════════════════════════════════════════════════════════════════════
                    """, identifiant, roleDEntree, Role.SUPER_ADMIN, email);
        } catch (Exception e) {
            log.warn("""

                    ════════════════════════════════════════════════════════════════════
                      Compte « {} » ouvert dans le royaume, avec les rôles {} et {}.
                      Accès NON envoyés à {} ({}).
                      Mot de passe : {}
                      À relever maintenant — il n'est affiché qu'ici, et Keycloak en
                      réclamera le changement à la première connexion.
                    ════════════════════════════════════════════════════════════════════
                    """, identifiant, roleDEntree, Role.SUPER_ADMIN, email, e.getMessage(),
                    motDePasse);
        }
    }

    // ------------------------------------------------------------------ l'API d'administration

    /**
     * Ouvre une session d'administration au nom du compte de service.
     *
     * <p>{@code client_credentials} et non un compte nommé : aucun mot de passe d'administrateur
     * n'a alors à vivre dans la configuration de ce service.</p>
     */
    private String jetonDAdministration(String royaumeUri) {
        MultiValueMap<String, String> formulaire = new LinkedMultiValueMap<>();
        formulaire.add("grant_type", "client_credentials");
        formulaire.add("client_id", client);
        formulaire.add("client_secret", secretDuClient);

        JsonNode reponse = http.post()
                .uri(URI.create(royaumeUri + "/protocol/openid-connect/token"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulaire)
                .retrieve()
                .body(JsonNode.class);

        if (reponse == null || !reponse.hasNonNull("access_token")) {
            throw new IllegalStateException("le royaume n'a pas délivré de jeton pour le client « "
                    + client + " » — le compte de service est-il activé ?");
        }
        return reponse.get("access_token").asText();
    }

    private boolean roleDejaPorte(String administration, String jeton) {
        JsonNode porteurs = lire(administration + "/roles/" + roleDEntree + "/users?max=1", jeton);
        return porteurs != null && porteurs.isArray() && !porteurs.isEmpty();
    }

    private void creerLeRoleSiAbsent(String administration, String jeton, String nomDuRole,
                                     String description) {
        if (lire(administration + "/roles/" + nomDuRole, jeton) != null) {
            return;
        }
        http.post()
                .uri(URI.create(administration + "/roles"))
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", nomDuRole, "description", description))
                .retrieve()
                .toBodilessEntity();
        log.info("Rôle « {} » créé dans le royaume.", nomDuRole);
    }

    private String utilisateurExistant(String administration, String jeton) {
        JsonNode trouves = lire(
                administration + "/users?exact=true&username=" + identifiant, jeton);
        if (trouves == null || !trouves.isArray() || trouves.isEmpty()) {
            return null;
        }
        return trouves.get(0).get("id").asText();
    }

    private String creerLUtilisateur(String administration, String jeton) {
        // Le nom complet est coupé au premier espace : Keycloak tient prénom et nom séparés, et
        // l'écran de la console afficherait « Super administrateur » entier dans la case prénom.
        String[] parties = nom == null ? new String[0] : nom.trim().split("\\s+", 2);
        String prenom = parties.length > 0 ? parties[0] : "";
        String patronyme = parties.length > 1 ? parties[1] : "";

        var reponse = http.post()
                .uri(URI.create(administration + "/users"))
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", identifiant,
                        "email", email,
                        "firstName", prenom,
                        "lastName", patronyme,
                        "enabled", true,
                        // Non vérifiée : c'est le premier message reçu qui vaudra vérification, et
                        // déclarer vraie une adresse jamais éprouvée ferait perdre la réinitialisation.
                        "emailVerified", false))
                .retrieve()
                .toBodilessEntity();

        URI creee = reponse.getHeaders().getLocation();
        if (creee == null) {
            throw new IllegalStateException("le royaume n'a pas dit où le compte a été créé");
        }
        String chemin = creee.getPath();
        return chemin.substring(chemin.lastIndexOf('/') + 1);
    }

    /**
     * Pose le mot de passe de première connexion.
     *
     * <p>{@code temporary} : Keycloak en réclame lui-même le changement dès la première
     * connexion. Ce qui a circulé par courriel ne vaut donc que le temps d'entrer une fois.</p>
     */
    private void poserLeMotDePasse(String administration, String jeton, String utilisateurId,
                                   String motDePasse) {
        http.put()
                .uri(URI.create(administration + "/users/" + utilisateurId + "/reset-password"))
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", motDePasse, "temporary", true))
                .retrieve()
                .toBodilessEntity();
    }

    private void attribuer(String administration, String jeton, String utilisateurId,
                           String... nomsDesRoles) {
        List<Map<String, Object>> aAttribuer = java.util.Arrays.stream(nomsDesRoles)
                .map(nomDuRole -> lire(administration + "/roles/" + nomDuRole, jeton))
                .filter(java.util.Objects::nonNull)
                .map(role -> Map.<String, Object>of(
                        "id", role.get("id").asText(), "name", role.get("name").asText()))
                .toList();

        if (aAttribuer.isEmpty()) {
            throw new IllegalStateException("aucun des rôles attendus n'existe dans le royaume");
        }

        http.post()
                .uri(URI.create(administration + "/users/" + utilisateurId
                        + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(aAttribuer)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Une lecture qui rend {@code null} plutôt que d'échouer sur un 404.
     *
     * <p>« Ce rôle n'existe pas encore » et « ce compte n'existe pas encore » sont des réponses
     * attendues au premier démarrage, pas des pannes.</p>
     */
    private JsonNode lire(String adresse, String jeton) {
        try {
            return http.get()
                    .uri(URI.create(adresse))
                    .header("Authorization", "Bearer " + jeton)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound absent) {
            return null;
        }
    }

    private String sansBarreFinale(String adresse) {
        String propre = adresse.trim();
        return propre.endsWith("/") ? propre.substring(0, propre.length() - 1) : propre;
    }
}
