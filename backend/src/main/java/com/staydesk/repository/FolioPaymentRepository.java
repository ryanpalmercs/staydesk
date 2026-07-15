package com.staydesk.repository;

import com.staydesk.model.FolioPayment;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FolioPaymentRepository extends ListCrudRepository<FolioPayment, Integer> {

    List<FolioPayment> findByFolioId(Integer folioId);

    Optional<FolioPayment> findByStripePaymentIntentId(String stripePaymentIntentId);

    @Modifying
    @Query("UPDATE folio_payments SET status = 'CAPTURED', captured_amount = :capturedAmount " +
           "WHERE stripe_payment_intent_id = :stripePaymentIntentId AND status <> 'CAPTURED'")
    int markCaptured(@Param("stripePaymentIntentId") String stripePaymentIntentId,
                     @Param("capturedAmount") BigDecimal capturedAmount);
}