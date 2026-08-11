package com.eventflow.booking.infrastructure.persistence;

import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingId;
import com.eventflow.booking.domain.model.BookingReference;
import com.eventflow.booking.domain.model.UserId;
import com.eventflow.booking.domain.port.out.BookingRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Implémente {@link BookingRepository} au moyen de Spring Data JPA. */
@Component
@RequiredArgsConstructor
class BookingPersistenceAdapter implements BookingRepository {

    private final BookingJpaRepository jpaRepository;

    @Override
    public Booking save(Booking booking) {
        BookingJpaEntity entity = jpaRepository.findById(booking.id().value())
                .map(existing -> {
                    BookingMapper.applyTo(existing, booking);
                    return existing;
                })
                .orElseGet(() -> BookingMapper.toJpa(booking));

        return BookingMapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Booking> findById(BookingId id) {
        return jpaRepository.findById(id.value()).map(BookingMapper::toDomain);
    }

    @Override
    public Optional<Booking> findByReference(BookingReference reference) {
        return jpaRepository.findByReference(reference.value()).map(BookingMapper::toDomain);
    }

    @Override
    public List<Booking> findByUser(UserId userId, int page, int size) {
        return jpaRepository
                .findByUserId(userId.value(),
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(BookingMapper::toDomain)
                .getContent();
    }

    @Override
    public long countByUser(UserId userId) {
        return jpaRepository.countByUserId(userId.value());
    }
}
