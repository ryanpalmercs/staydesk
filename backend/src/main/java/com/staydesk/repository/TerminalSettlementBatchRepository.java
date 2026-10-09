package com.staydesk.repository;

import com.staydesk.model.TerminalSettlementBatch;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDate;
import java.util.List;

public interface TerminalSettlementBatchRepository extends ListCrudRepository<TerminalSettlementBatch, Integer> {

    List<TerminalSettlementBatch> findByBatchDate(LocalDate batchDate);
}
