package com.qualiapproche.licences.config;

import com.qualiapproche.licences.model.EntreeDeJournal;
import com.qualiapproche.licences.service.JournalService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Inscrit au journal chaque action qui <b>modifie</b> quelque chose.
 *
 * <p>Un aspect plutôt que des appels dispersés dans les contrôleurs : ce qu'on écrit à la main, on
 * finit par l'oublier — et l'action ajoutée le mois prochain serait précisément celle qui manque
 * le jour où l'on cherche. Ici, toute méthode d'écriture d'un contrôleur est journalisée du seul
 * fait d'exister.</p>
 *
 * <p><b>Les lectures ne le sont pas.</b> Elles représenteraient l'essentiel du volume — chaque
 * ouverture d'écran en produit plusieurs — et noieraient les quelques lignes qui racontent
 * réellement quelque chose. Deux exceptions, où lire <i>est</i> l'action : télécharger une licence
 * et relire la clé de vérification.</p>
 *
 * <p><b>Le corps des requêtes n'est jamais retenu</b>, et c'est délibéré : il porte des mots de
 * passe à la création d'un compte comme à sa réinitialisation. Un journal qui les recopierait
 * serait plus dangereux que l'absence de journal. On garde ce qui a été fait et sur quoi, jamais
 * avec quelles valeurs.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class JournalAspect {

    private final JournalService journal;

    /** Les lectures qui valent une action, et méritent donc d'être tracées. */
    private static final Map<String, String> LECTURES_SENSIBLES = Map.of(
            "fichier", "Télécharger le fichier d'une licence",
            "clePublique", "Lire la clé de vérification");

    /**
     * Ce que chaque méthode de contrôleur veut dire, en clair.
     *
     * <p>Le nom de la méthode ferait un intitulé illisible dans un écran d'audit : « basculer »
     * n'apprend rien à qui relit six mois plus tard.</p>
     */
    private static final Map<String, String> INTITULES = Map.ofEntries(
            Map.entry("LicenceController.emettre", "Émettre une licence"),
            Map.entry("LicenceController.revoquer", "Révoquer une licence"),
            Map.entry("LicenceController.envoyer", "Envoyer une licence par courriel"),
            Map.entry("LicenceController.verifier", "Vérifier une licence"),
            Map.entry("PartenaireController.creer", "Créer un partenaire"),
            Map.entry("PartenaireController.modifier", "Modifier un partenaire"),
            Map.entry("OffreController.creer", "Créer une offre"),
            Map.entry("OffreController.modifier", "Modifier une offre"),
            Map.entry("UtilisateurController.creer", "Créer un compte"),
            Map.entry("UtilisateurController.modifier", "Modifier un compte"),
            Map.entry("UtilisateurController.activer", "Activer ou suspendre un compte"),
            Map.entry("UtilisateurController.reinitialiser", "Réinitialiser un mot de passe"),
            Map.entry("UtilisateurController.supprimer", "Supprimer un compte"),
            Map.entry("RoleController.creer", "Créer un rôle"),
            Map.entry("RoleController.modifier", "Modifier un rôle"),
            Map.entry("RoleController.supprimer", "Supprimer un rôle"),
            Map.entry("ParametreController.modifier", "Modifier un réglage"),
            Map.entry("SessionController.connexion", "Ouvrir une session"),
            Map.entry("SessionController.deconnexion", "Fermer la session"),
            Map.entry("SessionController.changerLeMotDePasse", "Changer son mot de passe"));

    /** Le type d'objet touché, déduit du contrôleur. */
    private static final Map<String, String> OBJETS = Map.of(
            "LicenceController", "Licence",
            "PartenaireController", "Partenaire",
            "OffreController", "Offre",
            "UtilisateurController", "Compte",
            "RoleController", "Rôle",
            "ParametreController", "Réglage",
            "SessionController", "Session");

    @Around("within(com.qualiapproche.licences.web..*) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PatchMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.GetMapping))")
    public Object journaliser(ProceedingJoinPoint appel) throws Throwable {
        MethodSignature signature = (MethodSignature) appel.getSignature();
        String controleur = signature.getDeclaringType().getSimpleName();
        String methode = signature.getName();
        String cle = controleur + "." + methode;

        String intitule = intitule(signature, cle, methode);
        if (intitule == null) {
            // Une lecture ordinaire : on la laisse passer sans rien inscrire.
            return appel.proceed();
        }

        long debut = System.currentTimeMillis();
        try {
            Object resultat = appel.proceed();
            inscrire(intitule, controleur, appel, true, null, debut);
            return resultat;
        } catch (Throwable echec) {
            // Le refus est inscrit avant d'être relancé : c'est souvent lui qu'on vient chercher.
            inscrire(intitule, controleur, appel, false, echec.getMessage(), debut);
            throw echec;
        }
    }

    /** L'intitulé de l'action, ou {@code null} s'il n'y a rien à journaliser. */
    private String intitule(MethodSignature signature, String cle, String methode) {
        Method reflet = signature.getMethod();
        boolean lecture = reflet.isAnnotationPresent(
                org.springframework.web.bind.annotation.GetMapping.class);
        if (lecture) {
            return LECTURES_SENSIBLES.get(methode);
        }
        return INTITULES.getOrDefault(cle, cle);
    }

    private void inscrire(String intitule, String controleur, ProceedingJoinPoint appel,
                          boolean abouti, String motif, long debut) {
        HttpServletRequest requete = requeteCourante();
        journal.inscrire(EntreeDeJournal.builder()
                .quand(journal.maintenant())
                .auteur(auteurCourant())
                .action(intitule)
                .objet(OBJETS.get(controleur))
                .objetId(premierIdentifiant(appel))
                .requete(requete != null
                        ? requete.getMethod() + " " + requete.getRequestURI()
                        : controleur)
                .abouti(abouti)
                .motif(tronquer(motif))
                .adresse(requete != null ? adresseDe(requete) : null)
                .duree(System.currentTimeMillis() - debut)
                .build());
    }

    /**
     * L'identifiant de l'objet touché, pris parmi les arguments.
     *
     * <p>Le premier {@code UUID} rencontré : c'est celui du chemin — {@code /api/licences/{id}} —
     * et cette convention tient partout ici. Les autres arguments ne sont pas lus, le corps des
     * requêtes ne devant jamais entrer dans le journal.</p>
     */
    private String premierIdentifiant(ProceedingJoinPoint appel) {
        for (Object argument : appel.getArgs()) {
            if (argument instanceof UUID identifiant) {
                return identifiant.toString();
            }
        }
        return null;
    }

    private String auteurCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        // Spring tient l'anonyme pour « authentifié » sous le nom « anonymousUser » : sans ce
        // test, une connexion refusée serait attribuée à un utilisateur de ce nom, et l'on
        // chercherait longtemps qui il est.
        if (authentification == null || authentification instanceof AnonymousAuthenticationToken) {
            return "anonyme";
        }
        return authentification.getName();
    }

    private HttpServletRequest requeteCourante() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributs) {
            return attributs.getRequest();
        }
        return null;
    }

    /**
     * L'adresse d'origine, celle du mandataire écartée.
     *
     * <p>Derrière un proxy, {@code getRemoteAddr()} rend l'adresse du proxy et non celle du poste :
     * toutes les entrées se ressembleraient, et l'adresse ne distinguerait plus rien.</p>
     */
    private String adresseDe(HttpServletRequest requete) {
        String transmise = requete.getHeader("X-Forwarded-For");
        if (transmise != null && !transmise.isBlank()) {
            return transmise.split(",")[0].trim();
        }
        return requete.getRemoteAddr();
    }

    private String tronquer(String motif) {
        if (motif == null) {
            return null;
        }
        return motif.length() <= 500 ? motif : motif.substring(0, 497) + "…";
    }
}
