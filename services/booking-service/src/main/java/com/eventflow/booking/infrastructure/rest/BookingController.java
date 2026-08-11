package com.eventflow.booking.infrastructure.rest;

import com.eventflow.booking.domain.model.BookingId;
import com.eventflow.booking.domain.model.BookingReference;
import com.eventflow.booking.domain.model.UserId;
import com.eventflow.booking.domain.port.in.CancelBookingUseCase;
import com.eventflow.booking.domain.port.in.CreateBookingUseCase;
import com.eventflow.booking.domain.port.in.FindBookingsUseCase;
import com.eventflow.booking.infrastructure.rest.dto.BookingResponse;
import com.eventflow.booking.infrastructure.rest.dto.CancelBookingRequest;
import com.eventflow.booking.infrastructure.rest.dto.CreateBookingRequest;
import com.eventflow.booking.infrastructure.rest.dto.PagedResponse;
import com.eventflow.booking.infrastructure.rest.mapper.BookingRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Validated
@Tag(name = "Réservations", description = "Cycle de vie des réservations")
class BookingController {

    private final CreateBookingUseCase createBooking;
    private final CancelBookingUseCase cancelBooking;
    private final FindBookingsUseCase findBookings;
    private final BookingRestMapper mapper;

    @PostMapping
    @Operation(summary = "Réserver des places",
            description = "Le statut renvoyé indique l'issue : CONFIRMED si les places ont été "
                    + "obtenues, REJECTED si le stock était insuffisant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation traitée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content),
            @ApiResponse(responseCode = "404", description = "Événement ou catégorie inconnu", content = @Content),
            @ApiResponse(responseCode = "503", description = "Catalogue indisponible", content = @Content)
    })
    ResponseEntity<BookingResponse> book(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response =
                mapper.toResponse(createBooking.book(mapper.toCommand(request)));

        // 201 même pour un REJECTED : la ressource « réservation » a bien été
        // créée et reste consultable. Son statut, lui, dit l'issue métier.
        return ResponseEntity
                .created(URI.create("/api/v1/bookings/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une réservation")
    BookingResponse byId(@PathVariable UUID id) {
        return mapper.toResponse(findBookings.byId(BookingId.of(id)));
    }

    @GetMapping("/by-reference/{reference}")
    @Operation(summary = "Retrouver une réservation par sa référence imprimée")
    BookingResponse byReference(@PathVariable String reference) {
        return mapper.toResponse(findBookings.byReference(BookingReference.of(reference)));
    }

    @GetMapping
    @Operation(summary = "Lister les réservations d'un utilisateur, les plus récentes d'abord")
    PagedResponse<BookingResponse> ofUser(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.toResponse(findBookings.ofUser(UserId.of(userId), page, size));
    }

    @PostMapping("/{id}/cancellation")
    @Operation(summary = "Annuler une réservation")
    BookingResponse cancel(@PathVariable UUID id,
                           @Valid @RequestBody CancelBookingRequest request) {
        return mapper.toResponse(cancelBooking.cancel(BookingId.of(id), request.reason()));
    }
}
