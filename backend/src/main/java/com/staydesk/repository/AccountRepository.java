package com.staydesk.repository;

import com.staydesk.model.Account;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountRepository extends ListCrudRepository<Account, UUID> {
    @Modifying
    @Query("UPDATE accounts SET last_seen_release_notes_id = :releaseNotesId WHERE id = :id")
    void updateLastSeenReleaseNotesId(@Param("id") UUID id, @Param("releaseNotesId") Integer releaseNotesId);
}
