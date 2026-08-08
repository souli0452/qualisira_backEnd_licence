package com.qualiapproche.licences.config;

import com.qualiapproche.licences.model.Permission;
import com.qualiapproche.licences.model.PermissionQualiSira;
import com.qualiapproche.licences.model.Role;
import com.qualiapproche.licences.model.Utilisateur;
import com.qualiapproche.licences.repository.PermissionRepository;
import com.qualiapproche.licences.repository.RoleRepository;
import com.qualiapproche.licences.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ce qui doit exister avant la première connexion : les permissions, les rôles, et un super
 * administrateur pour ouvrir les comptes des autres.
 *
 * <p>Sans cela, la première mise en route serait une impasse : la gestion des comptes exige un
 * compte, qu'aucun écran ne permettrait de créer. C'est ce démarrage qui rompt le cercle.</p>
 *
 * <p>Trois temps, dans cet ordre :</p>
 * <ol>
 *   <li>les <b>permissions</b> du catalogue sont insérées si absentes, et leurs libellés remis à
 *       jour — la liste appartient au code, la table n'en est que le reflet ;</li>
 *   <li>les <b>rôles système</b> sont créés s'ils manquent. {@code SUPER_ADMIN} reçoit en outre,
 *       à chaque démarrage, <b>toutes</b> les permissions : une permission ajoutée par une
 *       nouvelle version doit lui revenir d'office, sans quoi une action livrée resterait fermée à
 *       tout le monde ;</li>
 *   <li>le <b>compte super administrateur</b> est créé s'il n'existe aucun compte le portant.</li>
 * </ol>
 *
 * <p>Les rôles créés ici ne sont pas réécrits ensuite, {@code SUPER_ADMIN} excepté : ce sont des
 * points de départ à retoucher depuis l'écran, pas un modèle imposé.</p>
 */
@Component
@RequiredArgsConstructor
@Order(0)
@Slf4j
public class HabilitationsInitiales implements CommandLineRunner {

    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#%+=?";

    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;

    @Value("${licences.admin.utilisateur:admin}")
    private String identifiantAdmin;

    @Value("${licences.admin.mot-de-passe:}")
    private String motDePasseAdmin;

    @Value("${licences.admin.nom:Super administrateur}")
    private String nomAdmin;

    @Value("${licences.admin.email:}")
    private String emailAdmin;

    @Value("${licences.auth.mode:local}")
    private String mode;

    @Override
    @Transactional
    public void run(String... args) {
        chargerLesPermissions();
        Role superAdmin = chargerLesRoles();
        creerLeSuperAdministrateur(superAdmin);
    }

    // ------------------------------------------------------------------ permissions

    private void chargerLesPermissions() {
        int ajoutees = 0;
        for (PermissionQualiSira reference : PermissionQualiSira.values()) {
            Permission permission = permissions.findByCodeIgnoreCase(reference.name())
                    .orElse(null);
            if (permission == null) {
                permissions.save(Permission.de(reference));
                ajoutees++;
                continue;
            }
            // Le libellé peut être reformulé d'une version à l'autre ; le code, jamais.
            permission.setDomaine(reference.getDomaine());
            permission.setLibelle(reference.getLibelle());
            permissions.save(permission);
        }
        if (ajoutees > 0) {
            log.info("{} permission(s) ajoutée(s) au catalogue ; {} au total.", ajoutees,
                    PermissionQualiSira.values().length);
        }
    }

    // ------------------------------------------------------------------ rôles

    private Role chargerLesRoles() {
        Role superAdmin = creerSiAbsent(Role.SUPER_ADMIN, "Super administrateur",
                "Gère les comptes, les rôles et tout le reste. Rôle de dernier recours : "
                        + "il ne se retire pas au dernier qui le porte.",
                toutes(), true);

        // Toutes les permissions lui reviennent à chaque démarrage : une action ajoutée par une
        // nouvelle version doit être ouverte sans intervention en base.
        Set<Permission> toutes = toutes();
        if (!superAdmin.codesDesPermissions().equals(codes(toutes))) {
            superAdmin.setPermissions(toutes);
            roles.save(superAdmin);
            log.info("Rôle SUPER_ADMIN aligné sur les {} permissions du catalogue.", toutes.size());
        }

        creerSiAbsent("EDITEUR", "Éditeur de licences",
                "Émet, envoie et révoque les licences ; tient le fichier des partenaires et le "
                        + "catalogue des offres. N'a pas la main sur les comptes.",
                choisir(PermissionQualiSira.PARTENAIRE_LIRE, PermissionQualiSira.PARTENAIRE_CREER,
                        PermissionQualiSira.PARTENAIRE_MODIFIER, PermissionQualiSira.OFFRE_LIRE,
                        PermissionQualiSira.OFFRE_CREER, PermissionQualiSira.OFFRE_MODIFIER,
                        PermissionQualiSira.LICENCE_LIRE, PermissionQualiSira.LICENCE_EMETTRE,
                        PermissionQualiSira.LICENCE_ENVOYER, PermissionQualiSira.LICENCE_REVOQUER,
                        PermissionQualiSira.LICENCE_VERIFIER),
                true);

        creerSiAbsent("LECTEUR", "Lecture seule",
                "Consulte les partenaires, les offres et les licences, sans rien pouvoir émettre "
                        + "ni modifier — pour le support et le commerce.",
                choisir(PermissionQualiSira.PARTENAIRE_LIRE, PermissionQualiSira.OFFRE_LIRE,
                        PermissionQualiSira.LICENCE_LIRE, PermissionQualiSira.LICENCE_VERIFIER),
                true);

        return superAdmin;
    }

    private Role creerSiAbsent(String code, String libelle, String description,
                               Set<Permission> permissionsDuRole, boolean systeme) {
        return roles.findByCodeIgnoreCase(code).orElseGet(() -> {
            Role role = roles.save(Role.builder()
                    .code(code)
                    .libelle(libelle)
                    .description(description)
                    .permissions(permissionsDuRole)
                    .systeme(systeme)
                    .build());
            log.info("Rôle « {} » créé avec {} permission(s).", libelle, permissionsDuRole.size());
            return role;
        });
    }

    private Set<Permission> toutes() {
        return new LinkedHashSet<>(permissions.findAllByOrderByDomaineAscCodeAsc());
    }

    private Set<Permission> choisir(PermissionQualiSira... references) {
        List<String> codes = Arrays.stream(references).map(Enum::name).toList();
        return new LinkedHashSet<>(permissions.findByCodeIn(codes));
    }

    private Set<String> codes(Set<Permission> lot) {
        Set<String> codes = new TreeSet<>();
        lot.forEach(permission -> codes.add(permission.getCode()));
        return codes;
    }

    // ------------------------------------------------------------------ super administrateur

    /**
     * Crée le compte de départ, s'il n'existe aucun super administrateur.
     *
     * <p>Le contrôle porte sur le <b>rôle</b> et non sur l'identifiant : une équipe qui a renommé
     * son compte d'origine, ou qui en a créé d'autres, ne doit pas voir « admin » réapparaître à
     * chaque démarrage.</p>
     *
     * <p>À défaut de mot de passe configuré, un mot de passe est tiré au hasard et annoncé une
     * fois dans le journal. Un mot de passe par défaut versionné serait un mot de passe public —
     * et cet outil signe les licences.</p>
     */
    private void creerLeSuperAdministrateur(Role superAdmin) {
        long existants = utilisateurs.countByRoles_CodeIgnoreCaseAndActifTrue(Role.SUPER_ADMIN);
        if (existants > 0) {
            return;
        }

        if ("keycloak".equalsIgnoreCase(mode)) {
            // Les comptes vivent alors dans le royaume, pas ici : en créer un localement
            // laisserait un compte que personne ne peut utiliser, avec un mot de passe que
            // personne ne connaît.
            log.info("""

                    ════════════════════════════════════════════════════════════════════
                      Rôle {} créé, avec toutes les permissions.
                      L'authentification passe par Keycloak : créez-y le rôle {} et
                      attribuez-le au compte qui doit gérer les comptes et les rôles.
                    ════════════════════════════════════════════════════════════════════
                    """, Role.SUPER_ADMIN, Role.SUPER_ADMIN);
            return;
        }

        // Le compte porte déjà l'identifiant voulu mais pas le rôle : on le lui rend plutôt que
        // d'échouer sur un identifiant déjà pris, sans issue depuis l'écran.
        Utilisateur compte = utilisateurs.findByIdentifiantIgnoreCase(identifiantAdmin).orElse(null);
        if (compte != null) {
            Set<Role> avec = new LinkedHashSet<>(compte.getRoles());
            avec.add(superAdmin);
            compte.setRoles(avec);
            compte.setActif(true);
            utilisateurs.save(compte);
            log.warn("Le compte « {} » existait sans le rôle {} : le rôle lui a été rendu.",
                    identifiantAdmin, Role.SUPER_ADMIN);
            return;
        }

        boolean tire = motDePasseAdmin == null || motDePasseAdmin.isBlank();
        String secret = tire ? tirerUnMotDePasse() : motDePasseAdmin;

        Utilisateur creee = Utilisateur.builder()
                .identifiant(identifiantAdmin.trim().toLowerCase())
                .motDePasse(encodeur.encode(secret))
                .nomComplet(nomAdmin)
                .email(emailAdmin != null && !emailAdmin.isBlank() ? emailAdmin : null)
                .roles(new LinkedHashSet<>(Set.of(superAdmin)))
                .actif(true)
                // Tant que le mot de passe est celui du démarrage, le compte n'est personnel à
                // personne : l'écran le fera changer à la première connexion.
                .doitChangerMotDePasse(true)
                .build();
        creee.prendreDate("démarrage");
        utilisateurs.save(creee);

        log.warn("""

                ════════════════════════════════════════════════════════════════════
                  Super administrateur créé — il gère les comptes de tous les autres.
                  Identifiant   : {}
                  Mot de passe  : {}
                  {}
                  À changer à la première connexion.
                ════════════════════════════════════════════════════════════════════
                """, creee.getIdentifiant(), secret,
                tire ? "Tiré au hasard : notez-le, il n'est affiché qu'ici."
                     : "Celui de LICENCES_ADMIN_MDP.");
    }

    private String tirerUnMotDePasse() {
        SecureRandom hasard = new SecureRandom();
        StringBuilder tire = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            tire.append(ALPHABET.charAt(hasard.nextInt(ALPHABET.length())));
        }
        return tire.toString();
    }
}
