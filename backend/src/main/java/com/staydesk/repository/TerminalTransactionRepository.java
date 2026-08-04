package com.staydesk.repository;

import com.staydesk.model.TerminalTransaction;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TerminalTransactionRepository extends ListCrudRepository<TerminalTransaction, Integer> {

    Optional<TerminalTransaction> findByFlowId(String flowId);

    List<TerminalTransaction> findByStatusAndCreatedAtBefore(TerminalTransaction.Status status, LocalDateTime threshold);
}
