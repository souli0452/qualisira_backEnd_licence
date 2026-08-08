package com.qualiapproche.licences.web;

import org.springframework.http.HttpStatus;

/**
 * Refus que l'utilisateur peut comprendre et corriger — code déjà pris, offre inactive, partenaire
 * introuvable.
 *
 * <p>Le message est rédigé pour être <b>affiché tel quel</b> : c'est la seule information dont
 * dispose celui qui remplit le formulaire pour savoir quoi changer.</p>
 */
public class ErreurMetier extends RuntimeException {

    private final HttpStatus statut;

    public ErreurMetier(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public ErreurMetier(String message, HttpStatus statut) {
        super(message);
        this.statut = statut;
    }

    public HttpStatus getStatut() {
        return statut;
    }
}
