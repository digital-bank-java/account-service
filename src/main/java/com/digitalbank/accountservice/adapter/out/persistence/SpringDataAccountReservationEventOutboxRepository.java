package com.digitalbank.accountservice.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAccountReservationEventOutboxRepository
        extends JpaRepository<AccountReservationEventOutboxJpaEntity, UUID> {
    List<AccountReservationEventOutboxJpaEntity> findByProcessingTokenOrderByCreatedAtAsc(UUID token);

    @Modifying(flushAutomatically = true)
    @Query(value = """
			with candidates as (
				select event_id from account_reservation_event_outbox
				where event_status in ('PENDING', 'FAILED') and available_at <= :now
				  and (processing_until is null or processing_until <= :now)
				order by created_at, event_id for update skip locked limit :limit
			)
			update account_reservation_event_outbox set processing_token = :token, processing_until = :lease
			where event_id in (select event_id from candidates)
			""", nativeQuery = true)
    int claimReady(
            @Param("limit") int limit,
            @Param("now") Instant now,
            @Param("token") UUID token,
            @Param("lease") Instant lease);

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    "update account_reservation_event_outbox set event_status='PUBLISHED', published_at=:publishedAt, last_error=null, processing_token=null, processing_until=null where event_id=:eventId and event_status in ('PENDING','FAILED') and processing_token=:token",
            nativeQuery = true)
    int markPublished(
            @Param("eventId") UUID eventId, @Param("token") UUID token, @Param("publishedAt") Instant publishedAt);

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    "update account_reservation_event_outbox set event_status='FAILED', attempt_count=attempt_count+1, available_at=:retryAt, last_error=:error, processing_token=null, processing_until=null where event_id=:eventId and event_status in ('PENDING','FAILED') and processing_token=:token",
            nativeQuery = true)
    int markFailed(
            @Param("eventId") UUID eventId,
            @Param("token") UUID token,
            @Param("error") String error,
            @Param("retryAt") Instant retryAt);
}
