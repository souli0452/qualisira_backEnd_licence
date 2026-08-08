package com.qualiapproche.licences.service;

import com.qualiapproche.licences.dto.ParametreVue;
import com.qualiapproche.licences.model.Parametre;
import com.qualiapproche.licences.model.TypeParametre;
import com.qualiapproche.licences.repository.ParametreRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Les réglages : les lire, et les modifier.
 *
 * <p>La <b>clé</b> n'est jamais créée ni renommée ici : la liste appartient au code, qui la sème au
 * démarrage. Ce qui s'administre, c'est la valeur. Permettre d'en ajouter une depuis l'écran
 * laisserait fabriquer un {@code COURRIEL_SITE_WEB} que rien ne lit, sans qu'aucun message ne le
 * signale.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParametreService {

    /** Au-delà, on refuse : un logo qui pèse davantage alourdit chaque courriel envoyé. */
    private static final int OCTETS_MAX_IMAGE = 512 * 1024;

    private final ParametreRepository parametres;

    @Transactional(readOnly = true)
    public List<ParametreVue> lister() {
        return parametres.findAllByOrderByLibelleAsc().stream().map(ParametreVue::de).toList();
    }

    /**
     * La valeur d'un réglage, ou une chaîne vide s'il n'est pas renseigné.
     *
     * <p>Vide plutôt que {@code null} : ce qui compose un pied de courriel enchaîne des
     * concaténations, et un {@code null} y ferait apparaître le mot « null » au bas d'un message
     * envoyé à un client.</p>
     */
    @Transactional(readOnly = true)
    public String valeur(String cle) {
        return parametres.findByCleIgnoreCase(cle)
                .map(Parametre::getValeur)
                .filter(valeur -> !valeur.isBlank())
                .map(String::trim)
                .orElse("");
    }

    /**
     * Modifie la valeur d'un réglage, désigné par sa clé.
     *
     * <p>Par la clé et non par l'identifiant : c'est elle que l'écran affiche et que le code cite,
     * et elle ne change pas d'une installation à l'autre.</p>
     */
    @Transactional
    public ParametreVue modifier(String cle, String valeur) {
        Parametre parametre = parametres.findByCleIgnoreCase(cle)
                .orElseThrow(() -> new ErreurMetier(
                        "Le réglage « " + cle + " » n'existe pas. La liste des réglages appartient "
                                + "à l'application ; seule leur valeur se modifie.",
                        HttpStatus.NOT_FOUND));

        String propre = valeur == null ? "" : valeur.trim();
        verifier(parametre.getType(), propre);

        parametre.setValeur(propre.isEmpty() ? null : propre);
        parametre.setModifieLe(LocalDateTime.now());
        parametre.setModifiePar(identiteCourante());
        log.info("Réglage « {} » modifié par {}", parametre.getCle(), identiteCourante());
        return ParametreVue.de(parametres.save(parametre));
    }

    /**
     * Ce que la nature du réglage impose.
     *
     * <p>Vérifié ici plutôt que dans l'écran : une valeur fautive ne se découvrirait sinon qu'au
     * moment de composer un courriel, loin de qui l'a saisie — et le message partirait avec une
     * adresse inexploitable.</p>
     */
    private void verifier(TypeParametre type, String valeur) {
        if (valeur.isEmpty()) {
            // Vider un réglage est légitime : la ligne disparaît alors du pied.
            return;
        }
        switch (type) {
            case COURRIEL -> {
                if (!valeur.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                    throw new ErreurMetier("« " + valeur + " » n'est pas une adresse de courriel.");
                }
            }
            case URL -> {
                if (!valeur.matches("https?://\\S+")) {
                    throw new ErreurMetier("L'adresse du site commence par « http:// » ou "
                            + "« https:// ».");
                }
            }
            case IMAGE -> verifierImage(valeur);
            default -> { }
        }
    }

    private void verifierImage(String valeur) {
        if (!valeur.startsWith("data:image/")) {
            throw new ErreurMetier("Le logo doit être une image déposée depuis cet écran. Une "
                    + "adresse distante serait bloquée par la plupart des messageries, et le pied "
                    + "de courriel arriverait sans image.");
        }
        int virgule = valeur.indexOf(',');
        if (virgule < 0) {
            throw new ErreurMetier("L'image est incomplète : son contenu manque.");
        }
        try {
            int octets = Base64.getDecoder().decode(valeur.substring(virgule + 1)).length;
            if (octets > OCTETS_MAX_IMAGE) {
                throw new ErreurMetier("Le logo pèse " + (octets / 1024) + " Ko, au-delà des "
                        + (OCTETS_MAX_IMAGE / 1024) + " Ko admis : il voyage dans chaque courriel "
                        + "envoyé.");
            }
        } catch (IllegalArgumentException e) {
            throw new ErreurMetier("L'image n'a pas pu être lue : redéposez-la.");
        }
    }

    private String identiteCourante() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        return authentification != null ? authentification.getName() : "démarrage";
    }
}
