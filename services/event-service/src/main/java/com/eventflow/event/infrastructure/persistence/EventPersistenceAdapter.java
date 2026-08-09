package com.eventflow.event.infrastructure.persistence;

import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.port.out.EventRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Adaptateur sortant : implémente le port
 * {@link EventRepository} déclaré par le domaine, au moyen de Spring Data JPA.
 *
 * <p>C'est le seul endroit de l'application où JPA et le domaine se rencontrent.
 *
 * <p>{@code @RequiredArgsConstructor} génère le constructeur sur les champs
 * {@code final} : on reste en injection par constructeur, avec les dépendances
 * visibles et la classe instanciable sans conteneur Spring. Rien à voir avec un
 * {@code @Autowired} sur un champ, qui masquerait les dépendances.
 */
@Component
@RequiredArgsConstructor
class EventPersistenceAdapter implements EventRepository {

    private final EventJpaRepository jpaRepository;

    @Override
    public Event save(Event event) {
        EventJpaEntity entity = jpaRepository.findByIdWithCategories(event.id().value())
                .map(existing -> {
                    EventMapper.applyTo(existing, event);
                    return existing;
                })
                .orElseGet(() -> EventMapper.toJpa(event));

        return EventMapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Event> findById(EventId id) {
        return jpaRepository.findByIdWithCategories(id.value()).map(EventMapper::toDomain);
    }

    @Override
    public List<Event> findAll(int page, int size) {
        return jpaRepository
                .findAllWithCategories(PageRequest.of(page, size, Sort.by("startsAt").ascending()))
                .map(EventMapper::toDomain)
                .getContent();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public boolean existsById(EventId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public void deleteById(EventId id) {
        jpaRepository.deleteById(id.value());
    }
}
