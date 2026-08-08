package com.qualiapproche.licences.config;

import com.qualiapproche.licences.model.ClesDeReglage;
import com.qualiapproche.licences.model.Parametre;
import com.qualiapproche.licences.model.TypeParametre;
import com.qualiapproche.licences.repository.ParametreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

/**
 * Les réglages que l'application sait lire, semés au premier démarrage.
 *
 * <p>Semer les <b>clés</b> et non seulement les valeurs : ce sont les clés que le code cite — le
 * pied de courriel demande {@code COURRIEL_CONTACT_EMAIL} — et personne ne peut les deviner. Elles
 * apparaissent donc dans l'écran d'administration, avec leur intitulé et leur usage, et il n'y a
 * plus qu'à les renseigner.</p>
 *
 * <p>Les <b>coordonnées</b> sont semées vides : inventer un téléphone ou une adresse ferait figurer
 * une donnée fausse au bas de courriels envoyés à des clients. Un réglage vide est simplement omis
 * du pied.</p>
 *
 * <p>Le <b>logo</b> fait exception et arrive rempli, avec celui de l'éditeur livré dans le produit :
 * ce n'est pas une donnée à deviner, c'est la marque de qui envoie. Il reste remplaçable depuis
 * l'écran, pour une déclinaison ou un changement d'identité visuelle.</p>
 *
 * <p>Idempotent : chaque clé absente est créée, celles qui existent ne sont jamais réécrites — un
 * redémarrage n'écrase pas ce qui a été saisi, et ne rétablit pas un logo qu'on a remplacé.</p>
 */
@Component
@RequiredArgsConstructor
@Order(1)
@Slf4j
public class ReglagesInitiaux implements CommandLineRunner {

    /** Le logo de l'éditeur, livré avec le produit et semé comme valeur de départ. */
    private static final String LOGO_LIVRE = "courriel/logo-quali-sira.png";

    private record Reglage(String cle, String libelle, String description, TypeParametre type) { }

    private static final List<Reglage> ATTENDUS = List.of(
            new Reglage(ClesDeReglage.COURRIEL_MARQUE, "Nom de l'éditeur",
                    "Affiché au bas des courriels de licence, au-dessus des coordonnées.",
                    TypeParametre.TEXTE),
            new Reglage(ClesDeReglage.COURRIEL_CONTACT_EMAIL, "Courriel de contact",
                    "Adresse à laquelle le destinataire d'une licence peut demander de l'aide. "
                            + "Vide, aucune adresse n'est proposée dans le pied.",
                    TypeParametre.COURRIEL),
            new Reglage(ClesDeReglage.COURRIEL_CONTACT_TELEPHONE, "Téléphone de contact",
                    "Numéro affiché au bas des courriels. Vide, la ligne est omise.",
                    TypeParametre.TELEPHONE),
            new Reglage(ClesDeReglage.COURRIEL_SITE, "Site web",
                    "Adresse du site, affichée au bas des courriels. Vide, la ligne est omise.",
                    TypeParametre.URL),
            new Reglage(ClesDeReglage.COURRIEL_LOGO, "Logo des courriels",
                    "Image affichée au bas des courriels de licence. Elle voyage dans le message : "
                            + "elle s'affiche donc même chez un destinataire dont le client de "
                            + "messagerie bloque les images distantes.",
                    TypeParametre.IMAGE));

    private final ParametreRepository parametres;

    @Override
    @Transactional
    public void run(String... args) {
        int ajoutes = 0;
        for (Reglage attendu : ATTENDUS) {
            Parametre existant = parametres.findByCleIgnoreCase(attendu.cle()).orElse(null);
            if (existant != null) {
                // L'intitulé et l'usage peuvent être reformulés d'une version à l'autre ; la
                // valeur, elle, appartient à qui l'a saisie.
                existant.setLibelle(attendu.libelle());
                existant.setDescription(attendu.description());
                existant.setType(attendu.type());
                parametres.save(existant);
                continue;
            }
            parametres.save(Parametre.builder()
                    .cle(attendu.cle())
                    .libelle(attendu.libelle())
                    .description(attendu.description())
                    .type(attendu.type())
                    .valeur(valeurDeDepart(attendu.cle()))
                    .build());
            ajoutes++;
        }
        if (ajoutes > 0) {
            log.info("{} réglage(s) ajouté(s) ; {} au total. Les coordonnées du pied de courriel "
                    + "sont à renseigner depuis l'écran d'administration.", ajoutes, ATTENDUS.size());
        }
    }

    private String valeurDeDepart(String cle) {
        if (!ClesDeReglage.COURRIEL_LOGO.equals(cle)) {
            return null;
        }
        try {
            byte[] octets = new ClassPathResource(LOGO_LIVRE).getInputStream().readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(octets);
        } catch (Exception e) {
            // Sans logo livré, le réglage reste vide : le pied part sans image, ce qui est
            // préférable à un démarrage refusé pour une question d'ornement.
            log.warn("Logo livré {} illisible ({}) : le réglage est semé vide.",
                    LOGO_LIVRE, e.getMessage());
            return null;
        }
    }
}
