package com.qualiapproche.licences.service;

import com.qualiapproche.licences.dto.CompteCree;
import com.qualiapproche.licences.dto.DemandeDeCompte;
import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.dto.UtilisateurVue;
import com.qualiapproche.licences.model.Role;
import com.qualiapproche.licences.model.Utilisateur;
import com.qualiapproche.licences.repository.RoleRepository;
import com.qualiapproche.licences.repository.UtilisateurRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Les comptes du back-office : qui entre, et avec quels rôles.
 *
 * <p>Trois refus sont posés ici plutôt que dans l'écran, parce qu'ils protègent l'outil lui-même
 * et non le confort de saisie :</p>
 * <ul>
 *   <li>on n'attribue pas le rôle de super administrateur si l'on ne l'est pas soi-même — sans
 *       quoi la permission « créer un compte » suffirait à se hisser au rang supérieur ;</li>
 *   <li>on ne suspend ni ne supprime le dernier super administrateur actif — la gestion des
 *       comptes se refermerait, sans personne pour la rouvrir ;</li>
 *   <li>on ne se suspend ni ne se supprime soi-même — une main qui glisse suffirait à se mettre
 *       dehors, et il faudrait une intervention en base pour rentrer.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UtilisateurService {

    /** Sans I, l, O ni 0 : un mot de passe se transmet souvent de vive voix ou recopié à la main. */
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#%+=?";

    private static final int LONGUEUR_TIREE = 16;
    private static final int LONGUEUR_MINIMALE = 8;

    private final UtilisateurRepository utilisateurs;
    private final RoleRepository roles;
    private final PasswordEncoder encodeur;
    private final SecureRandom hasard = new SecureRandom();

    /**
     * Une page de comptes, filtrée par le serveur.
     *
     * <p>La recherche est faite ici et non côté écran : filtrer le navigateur ne porterait que sur
     * la page affichée, et un compte absent de celle-ci passerait pour inexistant — ce qui, sur un
     * écran d'administration des accès, conduirait à en recréer un qui existe déjà.</p>
     */
    @Transactional(readOnly = true)
    public PageVue<UtilisateurVue> lister(String recherche, Pageable page) {
        return PageVue.de(utilisateurs.findAll(correspondA(recherche), page), UtilisateurVue::de);
    }

    private Specification<Utilisateur> correspondA(String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return (racine, requete, cb) -> cb.conjunction();
        }
        String motif = "%" + recherche.trim().toLowerCase() + "%";
        return (racine, requete, cb) -> cb.or(
                cb.like(cb.lower(racine.get("identifiant")), motif),
                cb.like(cb.lower(cb.coalesce(racine.get("nomComplet"), "")), motif),
                cb.like(cb.lower(cb.coalesce(racine.get("email"), "")), motif));
    }

    @Transactional(readOnly = true)
    public Utilisateur parId(UUID id) {
        return utilisateurs.findById(id)
                .orElseThrow(() -> new ErreurMetier("Compte introuvable.", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UtilisateurVue vue(UUID id) {
        return UtilisateurVue.de(parId(id));
    }

    @Transactional(readOnly = true)
    public Utilisateur parIdentifiant(String identifiant) {
        return utilisateurs.findByIdentifiantIgnoreCase(identifiant)
                .orElseThrow(() -> new ErreurMetier("Compte introuvable.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public CompteCree creer(DemandeDeCompte demande, String auteur) {
        String identifiant = normaliser(demande.identifiant());
        if (utilisateurs.existsByIdentifiantIgnoreCase(identifiant)) {
            throw new ErreurMetier("L'identifiant « " + identifiant + " » est déjà pris.",
                    HttpStatus.CONFLICT);
        }

        Set<Role> attribues = resoudre(demande.roles());
        interdireEscaladeVersSuperAdmin(attribues);

        boolean tire = demande.motDePasse() == null || demande.motDePasse().isBlank();
        String secret = tire ? tirerUnMotDePasse() : verifierLaSolidite(demande.motDePasse());

        Utilisateur compte = Utilisateur.builder()
                .identifiant(identifiant)
                .motDePasse(encodeur.encode(secret))
                .nomComplet(demande.nomComplet())
                .email(demande.email())
                .roles(attribues)
                // Celui qui crée le compte connaît le mot de passe : tant qu'il n'a pas changé,
                // « émise par » ne désigne personne avec certitude.
                .doitChangerMotDePasse(true)
                .actif(demande.actif() == null || demande.actif())
                .build();
        compte.prendreDate(auteur);

        Utilisateur enregistre = utilisateurs.save(compte);
        log.info("Compte « {} » créé par {} — rôles : {}", identifiant, auteur,
                enregistre.codesDesRoles());
        return new CompteCree(UtilisateurVue.de(enregistre), tire ? secret : null);
    }

    /**
     * Retouche le compte.
     *
     * <p>L'identifiant n'est jamais réécrit : il est inscrit dans « émise par » sur les licences
     * déjà signées, que rien ne rattraperait.</p>
     */
    @Transactional
    public UtilisateurVue modifier(UUID id, DemandeDeCompte demande) {
        Utilisateur compte = parId(id);
        compte.setNomComplet(demande.nomComplet());
        compte.setEmail(demande.email());

        if (demande.roles() != null) {
            Set<Role> attribues = resoudre(demande.roles());
            // Le contrôle vaut dans les deux sens : attribuer le rôle suprême comme le retirer au
            // dernier qui le porte.
            if (!memesRoles(compte.getRoles(), attribues)) {
                interdireEscaladeVersSuperAdmin(attribues);
                if (compte.estSuperAdmin() && attribues.stream().noneMatch(Role::estSuperAdmin)) {
                    verifierQuIlResteUnSuperAdmin(compte);
                }
            }
            compte.setRoles(attribues);
        }

        if (demande.actif() != null && demande.actif() != compte.isActif()) {
            changerLActivation(compte, demande.actif());
        }

        return UtilisateurVue.de(utilisateurs.save(compte));
    }

    @Transactional
    public UtilisateurVue activer(UUID id, boolean actif) {
        Utilisateur compte = parId(id);
        changerLActivation(compte, actif);
        return UtilisateurVue.de(utilisateurs.save(compte));
    }

    /**
     * Remet un mot de passe à celui qui a perdu le sien.
     *
     * <p>Rendu une seule fois, à celui qui le réinitialise : la base n'en garde que l'empreinte,
     * et le compte devra le changer à la première connexion.</p>
     */
    @Transactional
    public String reinitialiserLeMotDePasse(UUID id, String propose) {
        Utilisateur compte = parId(id);
        boolean tire = propose == null || propose.isBlank();
        String secret = tire ? tirerUnMotDePasse() : verifierLaSolidite(propose);

        compte.setMotDePasse(encodeur.encode(secret));
        compte.setDoitChangerMotDePasse(true);
        utilisateurs.save(compte);
        log.info("Mot de passe du compte « {} » réinitialisé par {}", compte.getIdentifiant(),
                identiteCourante());
        return tire ? secret : null;
    }

    /** Le changement que l'utilisateur fait lui-même — l'ancien mot de passe est exigé. */
    @Transactional
    public void changerSonMotDePasse(String identifiant, String ancien, String nouveau) {
        Utilisateur compte = parIdentifiant(identifiant);
        if (ancien == null || !encodeur.matches(ancien, compte.getMotDePasse())) {
            throw new ErreurMetier("Le mot de passe actuel est incorrect.", HttpStatus.UNAUTHORIZED);
        }
        String secret = verifierLaSolidite(nouveau);
        if (encodeur.matches(secret, compte.getMotDePasse())) {
            throw new ErreurMetier("Le nouveau mot de passe doit être différent de l'ancien.");
        }
        compte.setMotDePasse(encodeur.encode(secret));
        compte.setDoitChangerMotDePasse(false);
        utilisateurs.save(compte);
        log.info("Mot de passe changé par {}", identifiant);
    }

    @Transactional
    public void supprimer(UUID id) {
        Utilisateur compte = parId(id);
        interdireDeSEvincer(compte, "supprimer");
        if (compte.estSuperAdmin()) {
            verifierQuIlResteUnSuperAdmin(compte);
        }
        utilisateurs.delete(compte);
        log.info("Compte « {} » supprimé par {}", compte.getIdentifiant(), identiteCourante());
    }

    /** Trace de la dernière entrée ; sans effet si le compte n'est pas local (mode Keycloak). */
    @Transactional
    public void noterLaConnexion(String identifiant) {
        utilisateurs.findByIdentifiantIgnoreCase(identifiant).ifPresent(compte -> {
            compte.setDerniereConnexion(LocalDateTime.now());
            utilisateurs.save(compte);
        });
    }

    // ------------------------------------------------------------------ garde-fous

    private void changerLActivation(Utilisateur compte, boolean actif) {
        if (!actif) {
            interdireDeSEvincer(compte, "suspendre");
            if (compte.estSuperAdmin()) {
                verifierQuIlResteUnSuperAdmin(compte);
            }
        }
        compte.setActif(actif);
    }

    /**
     * Empêche de se mettre dehors soi-même.
     *
     * <p>Rien ne distingue le clic malheureux de l'intention : dans les deux cas, il faudrait
     * ensuite une intervention en base pour rentrer.</p>
     */
    private void interdireDeSEvincer(Utilisateur compte, String action) {
        String courant = identiteCourante();
        if (courant != null && courant.equalsIgnoreCase(compte.getIdentifiant())) {
            throw new ErreurMetier("Vous ne pouvez pas " + action + " votre propre compte.",
                    HttpStatus.CONFLICT);
        }
    }

    private void verifierQuIlResteUnSuperAdmin(Utilisateur compte) {
        long restants = utilisateurs.countByRoles_CodeIgnoreCaseAndActifTrue(Role.SUPER_ADMIN);
        if (compte.isActif()) {
            restants--;
        }
        if (restants <= 0) {
            throw new ErreurMetier(
                    "Ce compte est le dernier super administrateur actif. Désignez-en un autre "
                            + "avant, sans quoi plus personne ne pourrait gérer les comptes.",
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * Le rôle suprême ne s'attribue que par un pair.
     *
     * <p>Sans cela, la seule permission « créer un compte » suffirait à s'en fabriquer un qui peut
     * tout, puis à s'y connecter.</p>
     */
    private void interdireEscaladeVersSuperAdmin(Set<Role> demandes) {
        if (demandes.stream().noneMatch(Role::estSuperAdmin) || estSuperAdminCourant()) {
            return;
        }
        throw new ErreurMetier("Seul un super administrateur peut attribuer le rôle de super "
                + "administrateur.", HttpStatus.FORBIDDEN);
    }

    private boolean estSuperAdminCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null) {
            // Aucune session : on est au démarrage, dans le chargement initial.
            return true;
        }
        return authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(habilitation -> habilitation.equals("ROLE_" + Role.SUPER_ADMIN));
    }

    private String identiteCourante() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        return authentification != null ? authentification.getName() : null;
    }

    // ------------------------------------------------------------------ outils

    private Set<Role> resoudre(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new ErreurMetier("Un compte sans rôle n'ouvre rien : attribuez-en au moins un.");
        }
        Set<Role> trouves = new LinkedHashSet<>();
        codes.stream().filter(code -> code != null && !code.isBlank()).forEach(code ->
                trouves.add(roles.findByCodeIgnoreCase(code.trim())
                        .orElseThrow(() -> new ErreurMetier("Le rôle « " + code + " » n'existe pas.",
                                HttpStatus.NOT_FOUND))));
        if (trouves.isEmpty()) {
            throw new ErreurMetier("Un compte sans rôle n'ouvre rien : attribuez-en au moins un.");
        }
        return trouves;
    }

    /** Comparaison par code : deux entités lues séparément ne sont pas égales au sens de Java. */
    private boolean memesRoles(Set<Role> avant, Set<Role> apres) {
        return avant.stream().map(Role::getCode).collect(Collectors.toSet())
                .equals(apres.stream().map(Role::getCode).collect(Collectors.toSet()));
    }

    private String normaliser(String identifiant) {
        if (identifiant == null || identifiant.isBlank()) {
            throw new ErreurMetier("L'identifiant est obligatoire.");
        }
        String normalise = identifiant.trim().toLowerCase();
        if (!normalise.matches("[a-z0-9._-]{3,80}")) {
            throw new ErreurMetier("L'identifiant se compose de 3 à 80 lettres, chiffres, points, "
                    + "tirets ou soulignés — sans espace ni accent.");
        }
        return normalise;
    }

    private String verifierLaSolidite(String motDePasse) {
        if (motDePasse == null || motDePasse.trim().length() < LONGUEUR_MINIMALE) {
            throw new ErreurMetier("Le mot de passe fait au moins " + LONGUEUR_MINIMALE
                    + " caractères.");
        }
        return motDePasse.trim();
    }

    private String tirerUnMotDePasse() {
        StringBuilder tire = new StringBuilder(LONGUEUR_TIREE);
        for (int i = 0; i < LONGUEUR_TIREE; i++) {
            tire.append(ALPHABET.charAt(hasard.nextInt(ALPHABET.length())));
        }
        return tire.toString();
    }
}
