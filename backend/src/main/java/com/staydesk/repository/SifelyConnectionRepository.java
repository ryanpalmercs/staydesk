package com.staydesk.repository;

import com.staydesk.model.SifelyConnection;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface SifelyConnectionRepository extends CrudRepository<SifelyConnection, Integer> {

    @Query("SELECT * FROM sifely_connections LIMIT 1")
    Optional<SifelyConnection> findFirst();
}