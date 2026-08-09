package com.eventflow.event.infrastructure.rest.mapper;

import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.Money;
import com.eventflow.event.domain.model.TicketCategory;
import com.eventflow.event.domain.port.in.CreateEventUseCase.CreateEventCommand;
import com.eventflow.event.domain.port.in.FindEventsUseCase.EventPage;
import com.eventflow.event.domain.port.in.UpdateEventUseCase.UpdateEventCommand;
import com.eventflow.event.infrastructure.rest.dto.CreateEventRequest;
import com.eventflow.event.infrastructure.rest.dto.EventResponse;
import com.eventflow.event.infrastructure.rest.dto.PagedResponse;
import com.eventflow.event.infrastructure.rest.dto.TicketCategoryResponse;
import com.eventflow.event.infrastructure.rest.dto.UpdateEventRequest;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduction entre le contrat HTTP et les types du domaine.
 *
 * <p>C'est ici, et nulle part ailleurs, que les primitives du transport
 * (String, BigDecimal) deviennent des value objects ({@link Money}). Le
 * contrôleur reste une simple façade, le domaine ignore JSON.
 */
@Component
public class EventRestMapper {

    public CreateEventCommand toCommand(CreateEventRequest request) {
        List<CreateEventCommand.NewCategory> categories = request.categories().stream()
                .map(category -> new CreateEventCommand.NewCategory(
                        category.name(),
                        Money.of(category.price(),
                                Currency.getInstance(category.currencyOrDefault())),
                        category.capacity()))
                .toList();

        return new CreateEventCommand(
                request.title(), request.venue(), request.startsAt(), categories);
    }

    public UpdateEventCommand toCommand(EventId id, UpdateEventRequest request) {
        return new UpdateEventCommand(id, request.title(), request.venue(), request.startsAt());
    }

    public EventResponse toResponse(Event event) {
        return new EventResponse(
                event.id().value(),
                event.title(),
                event.venue(),
                event.startsAt(),
                event.totalCapacity(),
                event.totalAvailableSeats(),
                event.isSoldOut(),
                event.categories().stream().map(this::toResponse).toList());
    }

    public TicketCategoryResponse toResponse(TicketCategory category) {
        return new TicketCategoryResponse(
                category.id().value(),
                category.name(),
                category.price().amount(),
                category.price().currency().getCurrencyCode(),
                category.capacity(),
                category.availableSeats(),
                category.isSoldOut());
    }

    public PagedResponse<EventResponse> toResponse(EventPage page) {
        return new PagedResponse<>(
                page.events().stream().map(this::toResponse).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
