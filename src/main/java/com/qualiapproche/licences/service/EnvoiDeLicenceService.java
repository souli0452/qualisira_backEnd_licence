package com.qualiapproche.licences.service;

import com.qualiapproche.licences.model.Licence;
import com.qualiapproche.licences.model.ModuleQualiSira;
import com.qualiapproche.licences.model.TypeLicence;
import com.qualiapproche.licences.repository.LicenceRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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

    /** L'expéditeur, le pied et le logo — communs à tous les courriels partant d'ici. */
    private final CourrielQualiSira habillage;

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

            habillage.appliquerExpediteur(aide);
            aide.setTo(adresse);
            aide.setSubject("Votre licence QualiSira — " + licence.getReference());
            aide.setText(corps(licence), true);
            aide.addAttachment(licence.getReference() + ".lic",
                    new ByteArrayResource(licence.getJeton().getBytes(StandardCharsets.UTF_8)),
                    "text/plain");
            habillage.joindreLeLogo(aide);

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
                habillage.pied("Une question sur cette licence ? Écrivez-nous."));
    }
}
