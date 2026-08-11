package com.qualiapproche.licences.service;

import com.qualiapproche.licences.model.ClesDeReglage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * L'habillage que partagent tous les courriels partant d'ici : l'expéditeur, le pied de message
 * et le logo.
 *
 * <p>Rassemblé en un seul endroit parce que ces trois éléments ne dépendent pas du message :
 * qu'on remette une licence à un partenaire ou ses accès à un administrateur, le destinataire doit
 * lire la même marque et savoir à qui écrire. Recopiés dans chaque envoi, ils auraient divergé au
 * premier changement de numéro de téléphone.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CourrielQualiSira {

    /** Identifiant de la pièce jointe, référencé par {@code cid:} dans le corps. */
    static final String LOGO_CID = "logoQualiSira";

    /**
     * Les coordonnées et le logo du pied de message.
     *
     * <p>Tenus en base et non en configuration : ces courriels sont souvent le premier contact
     * technique d'une installation, celui qui les reçoit doit savoir à qui écrire — et changer un
     * numéro de téléphone ne doit pas demander de livrer une version.</p>
     */
    private final ParametreService reglages;

    @Value("${spring.mail.username:}")
    private String adresseExpediteur;

    @Value("${licences.envoi.expediteur:}")
    private String expediteurDeclare;

    /**
     * Pose l'expéditeur affiché : celui qui est déclaré, à défaut le compte SMTP.
     *
     * <p>Ni l'un ni l'autre n'est obligatoire — sans expéditeur, le serveur SMTP pose le sien, ce
     * qui vaut mieux qu'un envoi refusé.</p>
     */
    public void appliquerExpediteur(MimeMessageHelper aide) throws Exception {
        if (expediteurDeclare != null && !expediteurDeclare.isBlank()) {
            aide.setFrom(expediteurDeclare);
        } else if (adresseExpediteur != null && !adresseExpediteur.isBlank()) {
            aide.setFrom(adresseExpediteur);
        }
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
    public String pied() {
        return pied("Une question ? Écrivez-nous.");
    }

    /**
     * Le même pied, avec sa propre invitation à écrire.
     *
     * @param invite la phrase qui précède les coordonnées ; le message qui remet une licence et
     *               celui qui remet des accès n'appellent pas la même
     */
    public String pied(String invite) {
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
                ? "<div>" + invite + "</div>"
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
     * <p>Son absence n'empêche pas l'envoi : ce que porte le message est ce qui compte, et un
     * courriel refusé pour un ornement manquant serait une régression pour qui l'attend. Le pied
     * reste alors lisible, il n'y manque que la marque.</p>
     */
    public void joindreLeLogo(MimeMessageHelper aide) {
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
