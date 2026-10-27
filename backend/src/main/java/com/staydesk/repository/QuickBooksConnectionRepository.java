package com.staydesk.repository;

import com.staydesk.model.QuickBooksConnection;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface QuickBooksConnectionRepository extends CrudRepository<QuickBooksConnection, Integer> {

    @Query("SELECT * FROM quickbooks_connections LIMIT 1")
    Optional<QuickBooksConnection> findFirst();
}
