package com.staydesk.repository;

import com.staydesk.model.StripeConnection;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface StripeConnectionRepository extends CrudRepository<StripeConnection, Integer> {

    @Query("SELECT * FROM stripe_connections WHERE id = 1")
    Optional<StripeConnection> findFirst();
}
