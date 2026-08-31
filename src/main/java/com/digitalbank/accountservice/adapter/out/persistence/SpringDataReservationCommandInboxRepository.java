package com.digitalbank.accountservice.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReservationCommandInboxRepository extends JpaRepository<ReservationCommandInboxJpaEntity, UUID> {
	Optional<ReservationCommandInboxJpaEntity> findByReservationRequestId(String reservationRequestId);
}
