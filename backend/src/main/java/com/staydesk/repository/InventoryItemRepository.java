package com.staydesk.repository;

import com.staydesk.model.InventoryItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface InventoryItemRepository extends ListCrudRepository<InventoryItem, Integer> {

    @Query("SELECT * FROM inventory_items WHERE active = true")
    List<InventoryItem> findAllActive();
}
