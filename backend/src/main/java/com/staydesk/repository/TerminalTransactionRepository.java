package com.staydesk.repository;

import com.staydesk.model.TerminalTransaction;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TerminalTransactionRepository extends ListCrudRepository<TerminalTransaction, Integer> {

    Optional<TerminalTransaction> findByFlowId(String flowId);

    List<TerminalTransaction> findByStatusAndCreatedAtBefore(TerminalTransaction.Status status, LocalDateTime threshold);

    /**
     * Backfills folio_payment_id after the fact for a terminal transaction whose FolioPayment
     * row didn't exist yet when the terminal call was made (a brand-new authorize/sale - see
     * {@link com.staydesk.payment.PaymentProvider#authorize}). Matched by the terminal's own
     * reference_no, which PaymentService also stores as the resulting FolioPayment's transaction
     * id. A no-op (0 rows) for any provider other than the Ingenico terminal bridge, since only
     * it ever writes rows into this table.
     */
    @Modifying
    @Query("UPDATE terminal_transactions SET folio_payment_id = :folioPaymentId WHERE reference_no = :referenceNo AND folio_payment_id IS NULL")
    int linkToFolioPayment(@Param("referenceNo") String referenceNo, @Param("folioPaymentId") int folioPaymentId);

    @Modifying
    @Query("DELETE FROM terminal_transactions WHERE created_at < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
