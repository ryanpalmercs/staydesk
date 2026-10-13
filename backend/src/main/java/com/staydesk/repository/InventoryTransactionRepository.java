package com.staydesk.repository;

import com.staydesk.model.InventoryTransaction;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface InventoryTransactionRepository extends ListCrudRepository<InventoryTransaction, Integer> {

    List<InventoryTransaction> findByItemIdOrderByCreatedAtDesc(int itemId);
}
