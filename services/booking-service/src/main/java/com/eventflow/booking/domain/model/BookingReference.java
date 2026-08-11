package com.eventflow.booking.domain.model;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Référence lisible d'une réservation, du type {@code EF-7K2M9QX4}.
 *
 * <p>Distincte de l'identifiant technique : c'est elle qu'on imprime sur le
 * billet, qu'un client dicte au téléphone et qu'un agent recherche. Un UUID
 * serait illisible et impossible à transmettre oralement.
 *
 * <p>L'alphabet exclut {@code I}, {@code O}, {@code 0} et {@code 1}, qui se
 * confondent à la lecture.
 */
public record BookingReference(String value) {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final String PREFIX = "EF-";
    private static final Pattern FORMAT = Pattern.compile("EF-[A-HJ-NP-Z2-9]{8}");
    private static final SecureRandom RANDOM = new SecureRandom();

    public BookingReference {
        Objects.requireNonNull(value, "La référence est obligatoire");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Référence de réservation invalide : " + value);
        }
    }

    public static BookingReference generate() {
        StringBuilder builder = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new BookingReference(builder.toString());
    }

    public static BookingReference of(String value) {
        return new BookingReference(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
