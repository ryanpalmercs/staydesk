package com.staydesk.repository;

import com.staydesk.model.Account;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountRepository extends ListCrudRepository<Account, UUID> {
    @Modifying
    @Query("UPDATE accounts SET last_seen_app_version = :version WHERE id = :id")
    void updateLastSeenAppVersion(@Param("id") UUID id, @Param("version") String version);
}
