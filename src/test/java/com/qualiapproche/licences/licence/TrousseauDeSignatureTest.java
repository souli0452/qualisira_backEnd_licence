package com.qualiapproche.licences.licence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D'où vient la clé de signature, et ce que le service refuse de deviner.
 *
 * <p>La paire était engendrée dès que le fichier manquait. Cela se passe bien une fois, sur un
 * poste de travail. En production c'est l'inverse : un conteneur qui repart sans son volume trouve
 * le fichier absent, se forge une clé neuve, démarre normalement — et toutes les licences déjà
 * remises deviennent invérifiables, d'un coup, chez tous les clients. Rien ne le signale ici : le
 * service fonctionne parfaitement. C'est chez le client que ça casse.</p>
 */
class TrousseauDeSignatureTest {

    @TempDir
    Path repertoire;

    private static KeyPair paire() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String privee(KeyPair paire) {
        return Base64.getEncoder().encodeToString(paire.getPrivate().getEncoded());
    }

    private static String publique(KeyPair paire) {
        return Base64.getEncoder().encodeToString(paire.getPublic().getEncoded());
    }

    /** Le trousseau tel que Spring l'assemblerait, réglages compris. */
    private TrousseauDeSignature trousseau(Path fichier, boolean engendrerSiAbsent) {
        TrousseauDeSignature trousseau = new TrousseauDeSignature();
        ReflectionTestUtils.setField(trousseau, "fichier", fichier.toString());
        ReflectionTestUtils.setField(trousseau, "engendrerSiAbsent", engendrerSiAbsent);
        return trousseau;
    }

    /** Écrit un trousseau sur le disque, comme le ferait une sauvegarde restaurée. */
    private Path fichierDeCles(KeyPair paire) throws Exception {
        Path fichier = repertoire.resolve("cles.properties");
        Files.writeString(fichier, "cle.privee=%s%ncle.publique=%s%n"
                .formatted(privee(paire), publique(paire)));
        return fichier;
    }

    private Path fichierAbsent() {
        return repertoire.resolve("cles-editeur.properties");
    }

    @Test
    @DisplayName("Sans clé et sans autorisation d'en engendrer, le service refuse de démarrer")
    void aucuneCle_refusDeDemarrer() throws Exception {
        TrousseauDeSignature trousseau = trousseau(fichierAbsent(), false);

        // C'est tout l'objet du garde-fou : un démarrage qui échoue se remarque dans la minute,
        // une clé silencieusement remplacée jamais.
        assertThatThrownBy(trousseau::charger)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUCUNE CLÉ DE SIGNATURE")
                // Le message doit dire quoi faire, et d'abord la bonne chose : restaurer.
                .hasMessageContaining("RESTAUREZ")
                .hasMessageContaining("volume persistant")
                .hasMessageContaining("LICENCES_ENGENDRER_CLES");

        assertThat(Files.exists(fichierAbsent()))
                .as("rien ne doit être écrit quand on refuse de démarrer")
                .isFalse();
    }

    @Test
    @DisplayName("Deux clés qui ne forment pas une paire sont refusées au démarrage")
    void paireIncoherente_refusee() throws Exception {
        // Le fichier se recopie et s'édite à la main : prendre la privée d'un trousseau et la
        // publique d'un autre est l'erreur la plus facile, et la plus coûteuse — elle ne se voit
        // qu'après livraison, quand le produit refuse des licences pourtant authentiques.
        Path fichier = repertoire.resolve("cles.properties");
        Files.writeString(fichier, "cle.privee=%s%ncle.publique=%s%n"
                .formatted(privee(paire()), publique(paire())));

        assertThatThrownBy(trousseau(fichier, false)::charger)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne forment pas une paire");
    }

    @Test
    @DisplayName("Le fichier existant est chargé, et rien n'est engendré")
    void fichierExistant_charge() throws Exception {
        KeyPair enPlace = paire();
        TrousseauDeSignature trousseau = trousseau(fichierDeCles(enPlace), false);
        trousseau.charger();

        assertThat(trousseau.clePubliqueBase64()).isEqualTo(publique(enPlace));
    }

    @Test
    @DisplayName("L'engendrement reste possible, mais seulement quand on l'a demandé")
    void engendrementDemande_paireEcrite() throws Exception {
        Path fichier = fichierAbsent();
        TrousseauDeSignature trousseau = trousseau(fichier, true);

        trousseau.charger();

        assertThat(trousseau.clePubliqueBase64()).isNotBlank();
        assertThat(Files.readString(fichier))
                .contains("cle.privee=")
                .contains("cle.publique=" + trousseau.clePubliqueBase64());
    }

    @Test
    @DisplayName("La clé engendrée signe des licences que sa clé publique vérifie")
    void cleEngendree_signeEtVerifie() throws Exception {
        TrousseauDeSignature trousseau = trousseau(fichierAbsent(), true);
        trousseau.charger();

        ContenuDeLicence contenu = new ContenuDeLicence("LIC-2026-0001", "CHU-BF",
                "CHU du Burkina Faso", java.time.LocalDate.now(),
                java.time.LocalDate.now().plusDays(30), java.util.List.of("NON_CONFORMITE"),
                50, "COMMERCIALE", "Offre intégrale");

        String jeton = JetonDeLicence.signer(contenu, trousseau.clePrivee());

        // La boucle complète : ce que cet outil signe, le produit doit pouvoir le lire.
        assertThat(JetonDeLicence.lire(jeton, trousseau.clePubliqueBase64()).reference())
                .isEqualTo("LIC-2026-0001");
    }
}
