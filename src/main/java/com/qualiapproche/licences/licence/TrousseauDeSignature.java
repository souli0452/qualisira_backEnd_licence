package com.qualiapproche.licences.licence;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Properties;

/**
 * La paire de clés de l'éditeur : celle qui signe toutes les licences.
 *
 * <p>Elle est engendrée au premier démarrage et conservée dans un fichier hors du code, jamais
 * versionné. C'est <b>le</b> secret de ce dispositif : qui la détient peut émettre des licences
 * pour n'importe quel partenaire, n'importe quels modules, sans limite de durée. Elle se
 * sauvegarde comme on sauvegarde un certificat de signature — sa perte oblige à réémettre toutes
 * les licences en circulation, puisque la clé publique embarquée dans le produit ne vérifierait
 * plus rien.</p>
 *
 * <p>La clé publique, elle, est faite pour être diffusée : elle est embarquée dans l'application
 * livrée et permet de vérifier une licence sans permettre d'en fabriquer une.</p>
 */
@Component
@Slf4j
public class TrousseauDeSignature {

    private static final String ALGORITHME = "Ed25519";

    @Value("${licences.cles.fichier:./data/cles-editeur.properties}")
    private String fichier;

    private String clePriveeBase64;
    private String clePubliqueBase64;

    @PostConstruct
    void charger() throws Exception {
        Path chemin = Path.of(fichier);
        if (Files.exists(chemin)) {
            Properties proprietes = new Properties();
            try (var flux = Files.newInputStream(chemin)) {
                proprietes.load(flux);
            }
            this.clePriveeBase64 = proprietes.getProperty("cle.privee");
            this.clePubliqueBase64 = proprietes.getProperty("cle.publique");
            if (clePriveeBase64 == null || clePubliqueBase64 == null) {
                throw new IllegalStateException(
                        "Le fichier de clés " + chemin.toAbsolutePath() + " est incomplet.");
            }
            log.info("Clés de signature chargées depuis {}", chemin.toAbsolutePath());
            return;
        }

        // Premier démarrage. Les clés sont engendrées ici plutôt que livrées avec le code : une
        // clé versionnée est une clé publique, quel que soit le nom qu'on lui donne.
        KeyPair paire = KeyPairGenerator.getInstance(ALGORITHME).generateKeyPair();
        this.clePriveeBase64 = Base64.getEncoder().encodeToString(paire.getPrivate().getEncoded());
        this.clePubliqueBase64 = Base64.getEncoder().encodeToString(paire.getPublic().getEncoded());

        Files.createDirectories(chemin.toAbsolutePath().getParent());
        String contenu = """
                # Clés de signature des licences QualiSira — NE JAMAIS VERSIONNER, NE JAMAIS LIVRER.
                # La clé privée signe les licences : sa divulgation permet d'en fabriquer sans limite.
                # Sauvegardez ce fichier : sans lui, toutes les licences émises deviennent invérifiables.
                cle.privee=%s
                cle.publique=%s
                """.formatted(clePriveeBase64, clePubliqueBase64);
        Files.writeString(chemin, contenu, StandardCharsets.UTF_8);
        try {
            // Lisible du seul propriétaire, quand le système de fichiers le permet.
            chemin.toFile().setReadable(false, false);
            chemin.toFile().setReadable(true, true);
            chemin.toFile().setWritable(false, false);
            chemin.toFile().setWritable(true, true);
        } catch (Exception e) {
            log.warn("Droits du fichier de clés non restreints : {}", e.getMessage());
        }

        log.warn("""

                ════════════════════════════════════════════════════════════════════
                  Nouvelles clés de signature engendrées : {}
                  SAUVEGARDEZ CE FICHIER. Sa perte rend invérifiables toutes les
                  licences émises ; sa divulgation permet d'en forger.
                  Clé publique à embarquer dans QualiSira :
                  {}
                ════════════════════════════════════════════════════════════════════
                """, chemin.toAbsolutePath(), clePubliqueBase64);
    }

    public PrivateKey clePrivee() {
        return JetonDeLicence.clePriveeDepuis(clePriveeBase64);
    }

    /** À embarquer dans l'application livrée. Se diffuse sans risque. */
    public String clePubliqueBase64() {
        return clePubliqueBase64;
    }
}
