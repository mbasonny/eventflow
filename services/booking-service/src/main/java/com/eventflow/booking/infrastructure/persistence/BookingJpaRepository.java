package com.eventflow.booking.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository Spring Data — détail d'infrastructure, invisible du domaine. */
interface BookingJpaRepository extends JpaRepository<BookingJpaEntity, UUID> {

    Optional<BookingJpaEntity> findByReference(String reference);

    Page<BookingJpaEntity> findByUserId(String userId, Pageable pageable);

    long countByUserId(String userId);
}
