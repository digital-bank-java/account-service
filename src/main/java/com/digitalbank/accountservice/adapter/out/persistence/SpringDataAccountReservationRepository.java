package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAccountReservationRepository extends JpaRepository<AccountReservationJpaEntity, UUID> {

    Optional<AccountReservationJpaEntity> findByReservationRequestId(String reservationRequestId);

    @Query(value = """
			select *
			from account_reservations
			where status = 'ACTIVE' and expires_at <= :expiresBy
			order by expires_at, id
			for update skip locked
			limit :limit
			""", nativeQuery = true)
    List<AccountReservationJpaEntity> findExpiredActiveForUpdate(
            @Param("expiresBy") Instant expiresBy, @Param("limit") int limit);
}
