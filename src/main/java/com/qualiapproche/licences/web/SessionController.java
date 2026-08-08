package com.qualiapproche.licences.web;

import com.qualiapproche.licences.model.Role;
import com.qualiapproche.licences.model.Utilisateur;
import com.qualiapproche.licences.repository.RoleRepository;
import com.qualiapproche.licences.repository.UtilisateurRepository;
import com.qualiapproche.licences.service.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Ouverture et fermeture de la session du back-office, et ce qu'elle donne le droit de faire.
 *
 * <p>La session est portée par un <b>cookie</b> déposé par le serveur : le back-office présente
 * les identifiants une fois, et le navigateur transporte ensuite la session seul. Rien n'est
 * conservé côté client — ni mot de passe, ni jeton dans le stockage local, qu'un script tiers
 * pourrait lire.</p>
 *
 * <p>La réponse porte les <b>rôles et permissions</b> de l'utilisateur : l'écran s'en sert pour
 * masquer ce qui lui est fermé. Ce n'est qu'un confort d'affichage — le serveur refuse de toute
 * façon chaque appel dont la permission manque, et c'est lui qui tranche.</p>
 *
 * <p>En mode Keycloak, la connexion ne passe pas par ici : c'est Keycloak qui authentifie, et la
 * session est ouverte au retour de sa page. Seule la lecture de l'état sert alors.</p>
 */
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    /** Absent en mode Keycloak, où l'authentification n'est pas locale. */
    private final ObjectProvider<AuthenticationManager> authentification;

    private final UtilisateurRepository utilisateurs;
    private final UtilisateurService comptes;
    private final RoleRepository roles;

    private final SecurityContextRepository sessions = new HttpSessionSecurityContextRepository();

    @Value("${licences.auth.mode:local}")
    private String mode;

    @Value("${licences.auth.role-requis:LICENCES_EDITEUR}")
    private String roleRequis;

    @GetMapping
    public Map<String, Object> session(Authentication authentifie) {
        return decrire(authentifie);
    }

    /**
     * Comment on se connecte ici — la seule chose que l'écran puisse demander avant d'être entré.
     *
     * <p>Ouvert sans session, et c'est le but : l'écran de connexion doit savoir s'il présente un
     * formulaire ou un bouton vers Keycloak, et il ne le sait qu'en le demandant. Répondre 401
     * lui laisserait le choix entre deviner et afficher un formulaire que le serveur refusera.</p>
     *
     * <p>Rien n'est divulgué qu'un simple regard sur l'écran de connexion n'apprendrait déjà.</p>
     */
    @GetMapping("/mode")
    public Map<String, Object> mode() {
        return Map.of("mode", mode, "keycloak", !"local".equalsIgnoreCase(mode));
    }

    /**
     * Ouvre une session à partir d'un compte local.
     *
     * <p>Le contexte est explicitement enregistré dans la session : sans cela, l'authentification
     * vaudrait pour la seule requête en cours et l'appel suivant repartirait en 401.</p>
     */
    @PostMapping("/connexion")
    public Map<String, Object> connexion(@RequestBody Map<String, String> identifiants,
                                         HttpServletRequest requete, HttpServletResponse reponse) {
        AuthenticationManager gestionnaire = authentification.getIfAvailable();
        if (gestionnaire == null) {
            throw new ErreurMetier(
                    "Cette instance est adossée à Keycloak : la connexion s'y fait par le "
                            + "fournisseur d'identité, pas par un compte local.",
                    HttpStatus.BAD_REQUEST);
        }

        try {
            Authentication authentifie = gestionnaire.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            identifiants.get("utilisateur"), identifiants.get("motDePasse")));

            SecurityContext contexte = SecurityContextHolder.createEmptyContext();
            contexte.setAuthentication(authentifie);
            SecurityContextHolder.setContext(contexte);
            sessions.saveContext(contexte, requete, reponse);
            comptes.noterLaConnexion(authentifie.getName());

            log.info("Session ouverte pour {}", authentifie.getName());
            return decrire(authentifie);
        } catch (AuthenticationException e) {
            // Un message unique pour l'identifiant inconnu comme pour le mot de passe erroné :
            // les distinguer indiquerait à un tiers quels comptes existent. Le compte suspendu
            // fait exception : son titulaire doit savoir à qui s'adresser plutôt que ressaisir.
            if (e instanceof DisabledException) {
                throw new ErreurMetier("Ce compte est suspendu. Adressez-vous à un administrateur.",
                        HttpStatus.UNAUTHORIZED);
            }
            throw new ErreurMetier("Identifiant ou mot de passe incorrect.", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Changement de mot de passe par son titulaire — l'ancien est exigé.
     *
     * <p>Aucune permission n'est demandée : chacun peut changer le sien, et c'est justement ce
     * qu'attend de lui le compte créé avec un mot de passe provisoire.</p>
     */
    @PostMapping("/mot-de-passe")
    public Map<String, Object> changerLeMotDePasse(@RequestBody Map<String, String> corps,
                                                   Authentication authentifie) {
        if (!"local".equalsIgnoreCase(mode)) {
            throw new ErreurMetier("Les mots de passe sont tenus par Keycloak : le changement se "
                    + "fait depuis votre compte du royaume.", HttpStatus.BAD_REQUEST);
        }
        if (authentifie == null) {
            throw new ErreurMetier("Aucune session ouverte.", HttpStatus.UNAUTHORIZED);
        }
        comptes.changerSonMotDePasse(authentifie.getName(), corps.get("ancien"), corps.get("nouveau"));
        return Map.of("change", true);
    }

    /** Ferme la session et retire le cookie. */
    @PostMapping("/deconnexion")
    public Map<String, Object> deconnexion(HttpServletRequest requete) {
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("deconnecte", true);
    }

    /**
     * L'état de la session, tel que l'écran l'attend.
     *
     * <p>Les rôles et les permissions sont tirés des habilitations plutôt que de la base : en mode
     * Keycloak, l'utilisateur connecté n'a pas forcément de ligne dans la table des comptes.</p>
     *
     * <p>Seuls sont montrés les rôles qui <b>veulent dire quelque chose ici</b> : ceux du catalogue,
     * et celui qui ouvre la porte. Un compte Keycloak en porte toujours d'autres — {@code
     * offline_access}, {@code default-roles-…}, ceux du client {@code account} — qui n'ouvrent rien
     * dans cet outil ; les afficher noierait les deux seuls qui comptent sous une liste dont
     * l'utilisateur ne peut rien déduire. Ils restent bien sûr des habilitations à part entière :
     * c'est l'affichage qu'on trie, pas les droits.</p>
     */
    private Map<String, Object> decrire(Authentication authentifie) {
        Map<String, Object> session = new HashMap<>();
        session.put("utilisateur", authentifie != null ? authentifie.getName() : "inconnu");
        session.put("mode", mode);
        session.put("deconnexionPossible", true);

        List<String> roles = habilitations(authentifie).stream()
                .filter(habilitation -> habilitation.startsWith("ROLE_"))
                .map(habilitation -> habilitation.substring("ROLE_".length()))
                .filter(this::connuIci)
                .toList();
        List<String> permissions = habilitations(authentifie).stream()
                .filter(habilitation -> !habilitation.startsWith("ROLE_")
                        && !habilitation.startsWith("SCOPE_")
                        && !habilitation.equals("OIDC_USER"))
                .toList();
        session.put("roles", roles);
        session.put("permissions", permissions);
        session.put("superAdmin", roles.contains(Role.SUPER_ADMIN));

        Optional<Utilisateur> compte = authentifie != null
                ? utilisateurs.findByIdentifiantIgnoreCase(authentifie.getName())
                : Optional.empty();
        session.put("nomComplet", compte.map(Utilisateur::getNomComplet).orElse(null));
        session.put("doitChangerMotDePasse",
                compte.map(Utilisateur::isDoitChangerMotDePasse).orElse(false));
        return session;
    }

    /**
     * Un rôle a-t-il un sens dans cet outil ?
     *
     * <p>Deux le peuvent : ceux du catalogue, qui portent des permissions, et le rôle d'entrée,
     * qui n'en porte aucune mais sans lequel on n'entre pas — c'est justement celui qu'un
     * administrateur cherche à vérifier quand un collègue se voit refuser la porte.</p>
     */
    private boolean connuIci(String code) {
        return code.equalsIgnoreCase(roleRequis) || roles.findByCodeIgnoreCase(code).isPresent();
    }

    private TreeSet<String> habilitations(Authentication authentifie) {
        TreeSet<String> triees = new TreeSet<>();
        if (authentifie != null) {
            authentifie.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .forEach(triees::add);
        }
        return triees;
    }
}
