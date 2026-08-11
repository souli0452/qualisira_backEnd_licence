package com.qualiapproche.licences.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Remet ses accès au super administrateur créé au premier démarrage.
 *
 * <p>Le mot de passe n'apparaissait jusqu'ici que dans le journal du premier démarrage. Sur un
 * poste de développement, cela suffit ; sur une installation livrée, le journal part souvent dans
 * un collecteur que l'exploitant ne lit pas au bon moment — et le compte devient alors
 * inatteignable, sans autre issue que d'aller réécrire une empreinte dans la base.</p>
 *
 * <p>Ce mot de passe est à usage unique : le compte porte {@code doitChangerMotDePasse}, et
 * l'écran en réclame le changement avant toute autre action. Ce qui circule ici ne vaut donc que
 * le temps de la première connexion — c'est ce qui rend l'envoi acceptable, et c'est pourquoi le
 * message le dit franchement à son destinataire.</p>
 *
 * <p>L'échec d'envoi n'est pas une erreur fatale : il est rendu à l'appelant, qui retombe alors
 * sur l'annonce dans le journal. Un service qui refuserait de démarrer parce que le serveur SMTP
 * est indisponible serait une régression — les licences déjà émises, elles, restent à servir.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvoiDuCompteService {

    private final JavaMailSender expediteur;
    private final CourrielQualiSira habillage;

    /**
     * L'adresse du back-office, pour que le message porte un lien cliquable.
     *
     * <p>En configuration et non déduite d'une requête : au démarrage, aucune requête n'est encore
     * arrivée — il n'y a donc rien d'où la déduire. Vide, le message reste juste, il n'y manque
     * que le lien.</p>
     */
    @Value("${licences.url:}")
    private String adresseDuBackOffice;

    /**
     * Envoie l'identifiant et le mot de passe de première connexion.
     *
     * @param destinataire adresse du compte à ouvrir
     * @param identifiant  ce qu'il saisira dans le premier champ
     * @param motDePasse   mot de passe à usage unique, à changer à la première connexion
     * @param parKeycloak  vrai si le compte vit dans le royaume : le message ne dit pas la même
     *                     chose, la connexion ne passant pas par le formulaire
     * @throws Exception telle que le serveur SMTP la donne ; l'appelant décide quoi en faire
     */
    public void envoyer(String destinataire, String identifiant, String motDePasse,
                        boolean parKeycloak) throws Exception {
        MimeMessage message = expediteur.createMimeMessage();
        MimeMessageHelper aide = new MimeMessageHelper(message, true, "UTF-8");

        habillage.appliquerExpediteur(aide);
        aide.setTo(destinataire);
        aide.setSubject("Vos accès à QualiSira Licences");
        aide.setText(corps(identifiant, motDePasse, parKeycloak), true);
        habillage.joindreLeLogo(aide);

        expediteur.send(message);
        log.info("Accès du compte « {} » envoyés à {}.", identifiant, destinataire);
    }

    /**
     * Le message reçu par le premier administrateur.
     *
     * <p>Il dit ce qu'il tient entre les mains — le compte qui ouvre tous les autres —, et ce
     * qu'il doit en faire tout de suite. Un mot de passe seul, sans cet avertissement, finirait
     * par rester en place.</p>
     */
    private String corps(String identifiant, String motDePasse, boolean parKeycloak) {
        String lien = adresseDuBackOffice == null || adresseDuBackOffice.isBlank()
                ? ""
                : "<p style=\"margin:0 0 20px\"><a href=\"" + adresseDuBackOffice
                  + "\" style=\"background:#1e3a5f;color:#fff;text-decoration:none;padding:10px 18px;"
                  + "border-radius:8px;display:inline-block\">Ouvrir le back-office</a></p>";

        String ou = parKeycloak
                ? "Vous vous connecterez par le bouton <strong>Se connecter avec Keycloak</strong>, "
                  + "avec les identifiants ci-dessous."
                : "Vous vous connecterez par le formulaire, avec les identifiants ci-dessous.";

        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#334155;
                            max-width:640px;line-height:1.6">
                  <h2 style="color:#1e3a5f;margin:0 0 4px">Vos accès à QualiSira Licences</h2>
                  <p style="color:#64748b;margin:0 0 20px">Votre compte vient d'être ouvert. Il
                     porte le rôle <strong>super administrateur</strong> : c'est lui qui crée les
                     comptes de tous les autres et leur attribue leurs rôles.</p>

                  %s

                  <p style="margin:0 0 6px">%s</p>
                  <table style="border-collapse:collapse;width:100%%;margin-bottom:20px">
                    <tr><td style="padding:6px 0;color:#64748b">Identifiant</td>
                        <td style="padding:6px 0;font-weight:600">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#64748b">Mot de passe</td>
                        <td style="padding:6px 0;font-family:Menlo,Consolas,monospace;
                                   font-weight:600">%s</td></tr>
                  </table>

                  <p style="background:#fffbeb;border:1px solid #fde68a;border-radius:8px;
                            padding:10px;color:#92400e">Ce mot de passe ne vaut que pour la
                     <strong>première connexion</strong> : il vous sera demandé d'en choisir un
                     autre avant toute action. Supprimez ce message une fois le changement fait —
                     un mot de passe oublié dans une boîte aux lettres finit par y être retrouvé.</p>

                  %s
                </div>
                """.formatted(lien, ou, identifiant, motDePasse,
                habillage.pied("Un problème pour vous connecter ? Écrivez-nous."));
    }
}
