package com.cnc.tutor.dto;

/** Erreur renvoyée au front, affichée telle quelle dans le chat. */
public record ErrorResponse(String code, String message) {
}
