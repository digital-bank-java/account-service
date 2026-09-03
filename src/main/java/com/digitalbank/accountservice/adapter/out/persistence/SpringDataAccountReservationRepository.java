package com.digitalbank.accountservice.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountReservationRepository extends JpaRepository<AccountReservationJpaEntity, UUID> {

    Optional<AccountReservationJpaEntity> findByReservationRequestId(String reservationRequestId);
}
