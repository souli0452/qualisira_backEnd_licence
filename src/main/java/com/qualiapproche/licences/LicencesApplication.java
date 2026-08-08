package com.qualiapproche.licences;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Outil interne d'émission des licences QualiSira.
 *
 * <p>QualiSira s'installe chez le client : la base de données, les fichiers et le serveur lui
 * appartiennent. Aucun secret conservé là-bas ne protège donc quoi que ce soit — c'est pourquoi
 * les licences ne sont pas <b>chiffrées</b> mais <b>signées</b>. La clé privée qui les signe vit
 * ici, et nulle part ailleurs ; l'application livrée ne connaît que la clé publique, qui permet
 * de vérifier une licence sans permettre d'en fabriquer une.</p>
 *
 * <p>Cet outil n'est jamais déployé chez un partenaire.</p>
 */
@SpringBootApplication
public class LicencesApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicencesApplication.class, args);
    }
}
