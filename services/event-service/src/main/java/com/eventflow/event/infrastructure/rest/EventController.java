package com.eventflow.event.infrastructure.rest;

import com.eventflow.event.domain.model.CategoryId;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.port.in.CreateEventUseCase;
import com.eventflow.event.domain.port.in.DeleteEventUseCase;
import com.eventflow.event.domain.port.in.FindEventsUseCase;
import com.eventflow.event.domain.port.in.UpdateEventUseCase;
import com.eventflow.event.infrastructure.rest.dto.CreateEventRequest;
import com.eventflow.event.infrastructure.rest.dto.EventResponse;
import com.eventflow.event.infrastructure.rest.dto.PagedResponse;
import com.eventflow.event.infrastructure.rest.dto.TicketCategoryResponse;
import com.eventflow.event.infrastructure.rest.dto.UpdateEventRequest;
import com.eventflow.event.infrastructure.rest.mapper.EventRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptateur entrant HTTP.
 *
 * <p>Le contrôleur ne fait que trois choses : traduire la requête en commande,
 * appeler le cas d'usage, traduire le résultat en réponse. Aucune règle métier,
 * aucun {@code @Transactional} — la transaction appartient à la couche
 * applicative.
 *
 * <p>Il dépend des <strong>ports</strong>, pas des implémentations : les classes
 * de {@code application.service} sont package-private et invisibles d'ici.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
@Tag(name = "Événements", description = "Catalogue des événements et disponibilité des places")
class EventController {

    private final CreateEventUseCase createEvent;
    private final UpdateEventUseCase updateEvent;
    private final DeleteEventUseCase deleteEvent;
    private final FindEventsUseCase findEvents;
    private final EventRestMapper mapper;

    @PostMapping
    @Operation(summary = "Créer un événement")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Événement créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = mapper.toResponse(createEvent.create(mapper.toCommand(request)));

        // 201 avec l'en-tête Location : le client apprend l'URL de la ressource
        // créée sans avoir à la reconstruire.
        return ResponseEntity
                .created(URI.create("/api/v1/events/" + response.id()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Lister les événements, par date de début croissante")
    PagedResponse<EventResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.toResponse(findEvents.all(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un événement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Événement trouvé"),
            @ApiResponse(responseCode = "404", description = "Événement inconnu", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    EventResponse byId(@PathVariable UUID id) {
        return mapper.toResponse(findEvents.byId(EventId.of(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier les informations générales d'un événement")
    EventResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return mapper.toResponse(
                updateEvent.update(mapper.toCommand(EventId.of(id), request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un événement et ses catégories")
    @ApiResponse(responseCode = "204", description = "Événement supprimé")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteEvent.delete(EventId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/categories")
    @Operation(summary = "Lister les catégories de places d'un événement")
    List<TicketCategoryResponse> categories(@PathVariable UUID id) {
        return findEvents.byId(EventId.of(id)).categories().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}/categories/{categoryId}/availability")
    @Operation(summary = "Consulter la disponibilité d'une catégorie")
    TicketCategoryResponse availability(@PathVariable UUID id, @PathVariable UUID categoryId) {
        return mapper.toResponse(
                findEvents.category(EventId.of(id), CategoryId.of(categoryId)));
    }
}
