package com.qualiapproche.licences.service;

import com.qualiapproche.licences.model.ClesDeReglage;
import com.qualiapproche.licences.model.Licence;
import com.qualiapproche.licences.model.ModuleQualiSira;
import com.qualiapproche.licences.model.TypeLicence;
import com.qualiapproche.licences.repository.LicenceRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Remet la licence au partenaire, en pièce jointe.
 *
 * <p>Pièce jointe et non corps de message : une licence collée dans un courriel se fait replier
 * par les clients de messagerie, qui coupent les longues lignes. L'administrateur colle alors une
 * chaîne tronquée, l'installation la refuse, et personne ne comprend pourquoi — la signature ne
 * dit pas <i>où</i> le texte a été abîmé. Le fichier arrive intact.</p>
 *
 * <p>Le texte est tout de même repris dans le corps, à titre de secours pour qui ne peut pas
 * recevoir de pièce jointe.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvoiDeLicenceService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender expediteur;
    private final LicenceRepository licenceRepository;
    private final LicenceService licenceService;

    @Value("${spring.mail.username:}")
    private String adresseExpediteur;

    @Value("${licences.envoi.expediteur:}")
    private String expediteurDeclare;

    /**
     * Les coordonnées et le logo du pied de message.
     *
     * <p>Tenus en base et non en configuration : ce courriel est souvent le premier contact
     * technique d'une installation, celui qui le reçoit doit savoir à qui écrire — et changer un
     * numéro de téléphone ne doit pas demander de livrer une version.</p>
     */
    private final ParametreService reglages;

    /** Identifiant de la pièce jointe, référencé par {@code cid:} dans le corps. */
    private static final String LOGO_CID = "logoQualiSira";

    /**
     * Envoie la licence à son destinataire.
     *
     * @param destinataire adresse choisie ; à défaut, celle du contact du partenaire
     */
    @Transactional
    public Licence envoyer(UUID licenceId, String destinataire) {
        Licence licence = licenceService.parId(licenceId);

        String adresse = destinataire != null && !destinataire.isBlank()
                ? destinataire.trim()
                : licence.getPartenaire().getContactEmail();
        if (adresse == null || adresse.isBlank()) {
            throw new ErreurMetier(
                    "Aucune adresse : renseignez le courriel du contact de « "
                            + licence.getPartenaire().getRaisonSociale() + " », ou indiquez une "
                            + "adresse au moment de l'envoi.");
        }

        try {
            MimeMessage message = expediteur.createMimeMessage();
            MimeMessageHelper aide = new MimeMessageHelper(message, true, "UTF-8");

            if (expediteurDeclare != null && !expediteurDeclare.isBlank()) {
                aide.setFrom(expediteurDeclare);
            } else if (adresseExpediteur != null && !adresseExpediteur.isBlank()) {
                aide.setFrom(adresseExpediteur);
            }
            aide.setTo(adresse);
            aide.setSubject("Votre licence QualiSira — " + licence.getReference());
            aide.setText(corps(licence), true);
            aide.addAttachment(licence.getReference() + ".lic",
                    new ByteArrayResource(licence.getJeton().getBytes(StandardCharsets.UTF_8)),
                    "text/plain");
            joindreLeLogo(aide);

            expediteur.send(message);
        } catch (Exception e) {
            // Le message porte la cause telle que le serveur SMTP la donne : « authentification
            // refusée » et « hôte injoignable » n'appellent pas la même correction.
            log.error("Envoi de la licence {} à {} en échec : {}",
                    licence.getReference(), adresse, e.getMessage());
            throw new ErreurMetier(
                    "La licence n'a pas pu être envoyée à " + adresse + " : " + e.getMessage()
                            + ". Elle reste téléchargeable et copiable depuis cet écran.",
                    HttpStatus.BAD_GATEWAY);
        }

        licence.setEnvoyeeLe(LocalDateTime.now());
        licence.setEnvoyeeA(adresse);
        log.info("Licence {} envoyée à {}", licence.getReference(), adresse);
        return licenceRepository.save(licence);
    }

    /**
     * Le message reçu par l'administrateur du partenaire.
     *
     * <p>Il dit ce qu'il a acheté et ce qu'il doit en faire : c'est souvent le premier contact
     * technique de son installation, et il n'a pas la documentation sous les yeux.</p>
     */
    private String corps(Licence licence) {
        String modules = licence.getModules().stream()
                .map(ModuleQualiSira::getLibelle)
                .collect(Collectors.joining(", "));
        String utilisateurs = licence.getUtilisateursMax() == 0
                ? "sans limite" : licence.getUtilisateursMax() + " utilisateurs";
        String essai = licence.getType() == TypeLicence.ESSAI
                ? "<p style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:8px;"
                  + "padding:10px;color:#92400e\">Il s'agit d'une <strong>licence d'essai</strong>. "
                  + "À son terme, les actions seront suspendues et une licence définitive vous sera "
                  + "demandée — vos données, elles, restent consultables.</p>"
                : "";

        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#334155;
                            max-width:640px;line-height:1.6">
                  <h2 style="color:#1e3a5f;margin:0 0 4px">Votre licence QualiSira</h2>
                  <p style="color:#64748b;margin:0 0 20px">%s</p>

                  %s

                  <table style="border-collapse:collapse;width:100%%;margin-bottom:20px">
                    <tr><td style="padding:6px 0;color:#64748b">Référence</td>
                        <td style="padding:6px 0;font-weight:600">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#64748b">Période</td>
                        <td style="padding:6px 0;font-weight:600">du %s au %s</td></tr>
                    <tr><td style="padding:6px 0;color:#64748b">Modules</td>
                        <td style="padding:6px 0;font-weight:600">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#64748b">Utilisateurs</td>
                        <td style="padding:6px 0;font-weight:600">%s</td></tr>
                  </table>

                  <h3 style="color:#1e3a5f;font-size:15px;margin-bottom:6px">Comment l'installer</h3>
                  <ol style="padding-left:18px;margin-top:0">
                    <li>Connectez-vous à QualiSira avec un compte administrateur.</li>
                    <li>À l'invitation qui s'affiche, ouvrez le fichier joint
                        <strong>%s.lic</strong> et collez-en le contenu.</li>
                    <li>Validez : les modules s'ouvrent immédiatement, sans redémarrage.</li>
                  </ol>

                  <p style="color:#64748b;font-size:13px">Si la pièce jointe ne vous parvient pas,
                     le texte de la licence figure ci-dessous. Copiez-le en entier, d'un seul
                     tenant.</p>
                  <div style="font-family:Menlo,Consolas,monospace;font-size:11px;background:#f8fafc;
                              border:1px solid #e2e8f0;border-radius:8px;padding:10px;
                              word-break:break-all;color:#334155">%s</div>

                  %s
                </div>
                """.formatted(
                licence.getPartenaire().getRaisonSociale(),
                essai,
                licence.getReference(),
                licence.getDebut().format(JOUR),
                licence.getFin().format(JOUR),
                modules,
                utilisateurs,
                licence.getReference(),
                licence.getJeton(),
                pied());
    }

    /**
     * Le pied : la marque, et à qui s'adresser.
     *
     * <p>Une table plutôt que du {@code flex} : les clients de messagerie n'appliquent pas les
     * dispositions modernes, et le logo se retrouverait au-dessus du texte dans la moitié d'entre
     * eux. Les tables, elles, se comportent partout de la même façon.</p>
     *
     * <p>Le logo est référencé par {@code cid:} : l'image voyage dans le message. Une adresse
     * distante serait bloquée par défaut par la plupart des clients, et le pied arriverait
     * amputé.</p>
     */
    private String pied() {
        String marque = reglages.valeur(ClesDeReglage.COURRIEL_MARQUE);
        String email = reglages.valeur(ClesDeReglage.COURRIEL_CONTACT_EMAIL);
        String telephone = reglages.valeur(ClesDeReglage.COURRIEL_CONTACT_TELEPHONE);
        String site = reglages.valeur(ClesDeReglage.COURRIEL_SITE);

        // Chaque ligne n'apparaît que si elle est renseignée : un pied qui annoncerait un
        // téléphone vide vaudrait moins que pas de téléphone du tout.
        StringBuilder contacts = new StringBuilder();
        if (!email.isEmpty()) {
            contacts.append("<a href=\"mailto:").append(email)
                    .append("\" style=\"color:#1e3a5f;text-decoration:none\">")
                    .append(email).append("</a>");
        }
        if (!telephone.isEmpty()) {
            if (contacts.length() > 0) {
                contacts.append("<br>");
            }
            contacts.append(telephone);
        }
        if (!site.isEmpty()) {
            if (contacts.length() > 0) {
                contacts.append("<br>");
            }
            contacts.append("<a href=\"").append(site)
                    .append("\" style=\"color:#1e3a5f;text-decoration:none\">")
                    .append(site.replaceFirst("^https?://", "")).append("</a>");
        }

        boolean logo = !reglages.valeur(ClesDeReglage.COURRIEL_LOGO).isEmpty();
        String celluleLogo = logo
                ? ("<td style=\"padding:16px 12px 0 0;vertical-align:top;width:68px\">"
                   + "<img src=\"cid:" + LOGO_CID + "\" alt=\"" + (marque.isEmpty() ? "" : marque)
                   + "\" width=\"56\" style=\"display:block;width:56px;height:auto;border:0\"></td>")
                : "";
        String entete = marque.isEmpty() ? ""
                : "<div style=\"font-weight:600;color:#1e3a5f;font-size:13px\">" + marque + "</div>";
        String invitation = contacts.length() > 0
                ? "<div>Une question sur cette licence ? Écrivez-nous.</div>"
                  + "<div style=\"margin-top:6px\">" + contacts + "</div>"
                : "";

        return """
                <table style="border-collapse:collapse;width:100%%;margin-top:28px;
                              border-top:1px solid #e2e8f0">
                  <tr>
                    %s
                    <td style="padding:16px 0 0;vertical-align:top;font-size:12px;color:#64748b;
                               line-height:1.6">%s%s</td>
                  </tr>
                </table>
                <p style="color:#94a3b8;font-size:11px;margin-top:16px">
                   Message automatique — merci de ne pas y répondre.</p>
                """.formatted(celluleLogo, entete, invitation);
    }

    /**
     * Joint le logo au message, en ressource intégrée.
     *
     * <p>Intégré et non lié : une image distante serait bloquée par défaut par la plupart des
     * clients de messagerie, et le pied arriverait amputé chez la moitié des destinataires.</p>
     *
     * <p>Son absence n'empêche pas l'envoi : la licence est ce qui compte, et un courriel refusé
     * pour un ornement manquant serait une régression pour le partenaire qui l'attend. Le pied
     * reste alors lisible, il n'y manque que la marque.</p>
     */
    private void joindreLeLogo(MimeMessageHelper aide) {
        String valeur = reglages.valeur(ClesDeReglage.COURRIEL_LOGO);
        if (valeur.isEmpty()) {
            return;
        }
        try {
            // « data:image/png;base64,…​ » — le type déclaré est repris tel quel, le destinataire
            // devant savoir s'il reçoit un PNG ou un JPEG.
            int pointVirgule = valeur.indexOf(';');
            int virgule = valeur.indexOf(',');
            String type = pointVirgule > 5 ? valeur.substring(5, pointVirgule) : "image/png";
            byte[] octets = Base64.getDecoder().decode(valeur.substring(virgule + 1));
            aide.addInline(LOGO_CID, new ByteArrayResource(octets), type);
        } catch (Exception e) {
            log.warn("Logo non joint au courriel ({}) : le message part sans marque.",
                    e.getMessage());
        }
    }
}
