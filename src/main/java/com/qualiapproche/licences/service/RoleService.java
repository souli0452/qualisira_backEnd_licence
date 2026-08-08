package com.qualiapproche.licences.service;

import com.qualiapproche.licences.dto.DemandeDeRole;
import com.qualiapproche.licences.dto.PermissionVue;
import com.qualiapproche.licences.dto.RoleVue;
import com.qualiapproche.licences.model.Permission;
import com.qualiapproche.licences.model.Role;
import com.qualiapproche.licences.repository.PermissionRepository;
import com.qualiapproche.licences.repository.RoleRepository;
import com.qualiapproche.licences.repository.UtilisateurRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Les rôles et ce qu'ils ouvrent. */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UtilisateurRepository utilisateurs;

    @Transactional(readOnly = true)
    public List<RoleVue> lister() {
        return roles.findAllByOrderByLibelleAsc().stream()
                .map(role -> RoleVue.de(role, utilisateurs.countByRoles_Id(role.getId())))
                .toList();
    }

    /** Le catalogue complet, pour les cases à cocher de l'écran des rôles. */
    @Transactional(readOnly = true)
    public List<PermissionVue> catalogue() {
        return permissions.findAllByOrderByDomaineAscCodeAsc().stream()
                .map(PermissionVue::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public Role parId(UUID id) {
        return roles.findById(id)
                .orElseThrow(() -> new ErreurMetier("Rôle introuvable.", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Role parCode(String code) {
        return roles.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ErreurMetier("Le rôle « " + code + " » n'existe pas.",
                        HttpStatus.NOT_FOUND));
    }

    @Transactional
    public RoleVue creer(DemandeDeRole demande) {
        String code = normaliser(demande.code());
        if (roles.existsByCodeIgnoreCase(code)) {
            throw new ErreurMetier("Le code « " + code + " » est déjà attribué à un autre rôle.",
                    HttpStatus.CONFLICT);
        }
        Role role = Role.builder()
                .code(code)
                .libelle(demande.libelle())
                .description(demande.description())
                .permissions(resoudre(demande.permissions()))
                .systeme(false)
                .build();
        return RoleVue.de(roles.save(role), 0);
    }

    /**
     * Retouche le libellé et les permissions.
     *
     * <p>Le code n'est jamais réécrit : il désigne le rôle dans Keycloak, que cette base ne pilote
     * pas — le changer ici priverait silencieusement de leurs droits les comptes du royaume.</p>
     *
     * <p>Un rôle système garde ses permissions : elles lui sont rendues à chaque démarrage, et les
     * modifier ne donnerait qu'une illusion de changement, défaite au prochain redémarrage.</p>
     */
    @Transactional
    public RoleVue modifier(UUID id, DemandeDeRole demande) {
        Role role = parId(id);
        role.setLibelle(demande.libelle());
        role.setDescription(demande.description());

        if (role.isSysteme()) {
            if (role.estSuperAdmin()) {
                throw new ErreurMetier(
                        "Les permissions du super administrateur ne se retirent pas : il est le "
                                + "seul recours si la gestion des comptes se referme.",
                        HttpStatus.CONFLICT);
            }
        } else {
            role.setPermissions(resoudre(demande.permissions()));
        }

        return RoleVue.de(roles.save(role), utilisateurs.countByRoles_Id(role.getId()));
    }

    @Transactional
    public void supprimer(UUID id) {
        Role role = parId(id);
        if (role.isSysteme()) {
            throw new ErreurMetier("Le rôle « " + role.getLibelle() + " » est fourni par "
                    + "l'application : il ne se supprime pas.", HttpStatus.CONFLICT);
        }
        long comptes = utilisateurs.countByRoles_Id(id);
        if (comptes > 0) {
            throw new ErreurMetier("Ce rôle est porté par " + comptes + " compte(s) : retirez-le-leur "
                    + "avant de le supprimer.", HttpStatus.CONFLICT);
        }
        roles.delete(role);
    }

    /**
     * Traduit des codes en permissions existantes.
     *
     * <p>Un code inconnu arrête la demande au lieu d'être ignoré : un rôle amputé d'une permission
     * mal orthographiée n'ouvrirait rien, et personne ne saurait pourquoi.</p>
     */
    private Set<Permission> resoudre(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> demandes = codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Permission> trouvees = permissions.findByCodeIn(demandes);
        if (trouvees.size() != demandes.size()) {
            Set<String> connues = trouvees.stream().map(Permission::getCode)
                    .collect(Collectors.toSet());
            String inconnues = demandes.stream().filter(code -> !connues.contains(code))
                    .collect(Collectors.joining(", "));
            throw new ErreurMetier("Permission inconnue : " + inconnues);
        }
        return new LinkedHashSet<>(trouvees);
    }

    /** « CHEF_PROJET » : majuscules, sans accent ni espace — le code est une habilitation Spring. */
    private String normaliser(String code) {
        if (code == null || code.isBlank()) {
            throw new ErreurMetier("Le code du rôle est obligatoire.");
        }
        String normalise = Normalizer.normalize(code.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (normalise.isEmpty()) {
            throw new ErreurMetier("Le code du rôle ne contient aucun caractère utilisable.");
        }
        if (normalise.startsWith("ROLE_")) {
            // Spring préfixe lui-même les rôles : un code « ROLE_X » donnerait « ROLE_ROLE_X »,
            // et aucun contrôle ne correspondrait plus.
            throw new ErreurMetier("Le code d'un rôle ne commence pas par « ROLE_ » : ce préfixe "
                    + "est ajouté par le contrôle d'accès lui-même.");
        }
        return normalise;
    }
}
