package com.qualiapproche.licences.service;

import com.qualiapproche.licences.model.Utilisateur;
import com.qualiapproche.licences.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Les comptes tels que Spring Security les lit, à la connexion.
 *
 * <p>Chaque compte arrive avec <b>deux sortes d'habilitations</b> : ses rôles, préfixés
 * {@code ROLE_} comme Spring l'attend, et surtout les <b>permissions</b> de ces rôles, qui sont ce
 * que contrôlent les {@code @PreAuthorize}. Ne poser que les rôles obligerait à énumérer dans le
 * code tous les rôles autorisés pour chaque action — c'est précisément ce que la table des
 * permissions supprime.</p>
 *
 * <p>Un compte suspendu est rendu « désactivé » plutôt qu'introuvable : Spring refuse alors la
 * connexion de lui-même, et la distinction reste visible dans les journaux.</p>
 */
@Service
@RequiredArgsConstructor
public class ComptesDuBackOffice implements UserDetailsService {

    private final UtilisateurRepository utilisateurs;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifiant) throws UsernameNotFoundException {
        Utilisateur compte = utilisateurs.findByIdentifiantIgnoreCase(identifiant)
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu : " + identifiant));

        return User.withUsername(compte.getIdentifiant())
                .password(compte.getMotDePasse())
                .authorities(habilitations(compte))
                .disabled(!compte.isActif())
                .build();
    }

    /** Les rôles préfixés, et les permissions telles quelles. */
    public static Set<GrantedAuthority> habilitations(Utilisateur compte) {
        Set<GrantedAuthority> habilitations = new LinkedHashSet<>();
        compte.codesDesRoles().forEach(code ->
                habilitations.add(new SimpleGrantedAuthority("ROLE_" + code)));
        compte.codesDesPermissions().forEach(code ->
                habilitations.add(new SimpleGrantedAuthority(code)));
        return habilitations;
    }
}
