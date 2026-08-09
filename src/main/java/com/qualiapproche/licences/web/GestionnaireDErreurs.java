package com.qualiapproche.licences.web;

import com.qualiapproche.licences.licence.LicenceIllisibleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/** Rend les refus sous une forme unique, que le back-office affiche telle quelle. */
@RestControllerAdvice
@Slf4j
public class GestionnaireDErreurs {

    @ExceptionHandler(ErreurMetier.class)
    public ResponseEntity<Map<String, Object>> metier(ErreurMetier e) {
        return ResponseEntity.status(e.getStatut()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(LicenceIllisibleException.class)
    public ResponseEntity<Map<String, Object>> licence(LicenceIllisibleException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /**
     * Permission manquante — 403, et non 500.
     *
     * <p>Sans ce traitement, le refus prononcé par un {@code @PreAuthorize} tomberait dans le
     * fourre-tout ci-dessous : l'écran afficherait « une erreur inattendue est survenue » là où
     * l'utilisateur doit lire qu'il lui manque un droit, et le journal se remplirait de traces
     * pour un fonctionnement normal.</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> refus(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                "Cette action ne vous est pas ouverte. Demandez la permission correspondante à un "
                        + "administrateur."));
    }

    /**
     * Les messages de validation sont regroupés par champ : « Le code est obligatoire » désigne
     * la case à corriger, là où une liste à plat oblige à chercher.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(erreur -> erreur.getField() + " : " + erreur.getDefaultMessage())
                .collect(Collectors.joining(" ; "));
        return ResponseEntity.badRequest().body(Map.of("message", "Saisie à corriger — " + detail));
    }

    /**
     * Un fichier demandé qui n'existe pas est un 404, jamais une panne du serveur.
     *
     * <p>Sans cette exception, toute ressource absente — une image, une police — repartait en 500
     * avec une trace dans le journal : on cherchait une défaillance là où il n'y avait qu'un
     * fichier manquant, et les vraies pannes se noyaient dans ce bruit.</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> introuvable(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Ressource introuvable : " + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> imprevue(Exception e) {
        log.error("Erreur inattendue", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Une erreur inattendue est survenue : " + e.getMessage()));
    }
}
