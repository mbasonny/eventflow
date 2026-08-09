package com.eventflow.event.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Enveloppe de pagination maison.
 *
 * <p>Renvoyer directement un {@code Page} de Spring Data exposerait une
 * structure interne au framework, dont la sérialisation JSON n'est pas stable
 * entre versions. Un contrat explicite reste maîtrisé.
 */
@Schema(description = "Page de résultats")
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
