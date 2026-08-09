package com.eventflow.event.application.service;

import com.eventflow.event.domain.exception.EventNotFoundException;
import com.eventflow.event.domain.model.CategoryId;
import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.TicketCategory;
import com.eventflow.event.domain.port.in.FindEventsUseCase;
import com.eventflow.event.domain.port.out.EventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectures du catalogue.
 *
 * <p>{@code readOnly = true} n'est pas décoratif : Hibernate passe la session en
 * mode {@code FlushMode.MANUAL} et cesse de tenir un instantané de chaque entité
 * pour détecter les modifications. Moins de mémoire, moins de travail au commit,
 * et aucune écriture accidentelle possible.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class EventQueryService implements FindEventsUseCase {

    private final EventRepository eventRepository;

    @Override
    public Event byId(EventId id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    @Override
    public EventPage all(int page, int size) {
        List<Event> events = eventRepository.findAll(page, size);
        return new EventPage(events, page, size, eventRepository.count());
    }

    @Override
    public TicketCategory category(EventId eventId, CategoryId categoryId) {
        // Passer par l'agrégat garantit que la catégorie appartient bien à cet
        // événement : une catégorie d'un autre événement lève CategoryNotFound.
        return byId(eventId).category(categoryId);
    }
}
