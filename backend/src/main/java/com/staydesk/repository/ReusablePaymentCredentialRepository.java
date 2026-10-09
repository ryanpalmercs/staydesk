package com.staydesk.repository;

import com.staydesk.model.ReusablePaymentCredential;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReusablePaymentCredentialRepository extends ListCrudRepository<ReusablePaymentCredential, Integer> {

    List<ReusablePaymentCredential> findByFolioIdAndRevokedFalse(int folioId);

    List<ReusablePaymentCredential> findByExpiresAtBeforeAndRevokedFalse(LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE reusable_payment_credentials SET expires_at = :expiresAt WHERE folio_id = :folioId AND revoked = false")
    int scheduleExpiry(@Param("folioId") int folioId, @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Query("UPDATE reusable_payment_credentials SET revoked = true, revoked_at = :revokedAt WHERE id = :id")
    int markRevoked(@Param("id") int id, @Param("revokedAt") LocalDateTime revokedAt);
}