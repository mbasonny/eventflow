package com.eventflow.event.application.service;

import com.eventflow.event.domain.exception.EventNotFoundException;
import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.TicketCategory;
import com.eventflow.event.domain.port.in.CreateEventUseCase;
import com.eventflow.event.domain.port.in.DeleteEventUseCase;
import com.eventflow.event.domain.port.in.UpdateEventUseCase;
import com.eventflow.event.domain.port.out.EventRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écritures du catalogue.
 *
 * <p>Le service <strong>orchestre</strong> : il charge l'agrégat, lui demande
 * d'appliquer la règle, le sauvegarde. Il ne contient aucune règle métier —
 * celles-ci vivent dans {@link Event} et {@link TicketCategory}. Si de la
 * logique apparaissait ici, ce serait le signe d'un domaine anémique.
 *
 * <p>{@code @Transactional} est posé <strong>à ce niveau</strong> : ni sur le
 * contrôleur (qui ne doit pas connaître la persistance), ni sur le repository
 * (dont la granularité serait trop fine pour garantir l'atomicité d'un cas
 * d'usage).
 *
 * <p>Les trois opérations sont regroupées parce qu'elles partagent une même
 * raison de changer : les règles d'écriture du catalogue. Les réserver à trois
 * classes d'une méthode chacune serait de la cérémonie.
 */
@Service
@RequiredArgsConstructor
@Transactional
class EventCommandService implements CreateEventUseCase, UpdateEventUseCase, DeleteEventUseCase {

    private final EventRepository eventRepository;
    private final Clock clock;

    @Override
    public Event create(CreateEventCommand command) {
        Event event = Event.create(
                command.title(), command.venue(), command.startsAt(), clock);

        command.categories().forEach(category -> event.addCategory(
                TicketCategory.create(category.name(), category.price(), category.capacity())));

        return eventRepository.save(event);
    }

    @Override
    public Event update(UpdateEventCommand command) {
        Event event = eventRepository.findById(command.id())
                .orElseThrow(() -> new EventNotFoundException(command.id()));

        event.rename(command.title());
        event.relocate(command.venue());
        event.reschedule(command.startsAt(), clock);

        return eventRepository.save(event);
    }

    @Override
    public void delete(EventId id) {
        if (!eventRepository.existsById(id)) {
            throw new EventNotFoundException(id);
        }
        eventRepository.deleteById(id);
    }
}
