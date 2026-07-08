package com.staydesk.repository;

import com.staydesk.model.Guest;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GuestRepository extends ListCrudRepository<Guest, Integer> {

    Optional<Guest> getGuestByEmail(String email);

    @Modifying
    @Query("UPDATE guests SET flagged = TRUE, flag_reason = :reason, flagged_date = now(), flagged_by = :flaggedBy WHERE id = :id")
    void flagGuest(@Param("id") Integer id, @Param("reason") String reason, @Param("flaggedBy") UUID flaggedBy);

    @Modifying
    @Query("UPDATE guests SET flagged = FALSE, flag_reason = NULL, flagged_date = NULL, flagged_by = NULL WHERE id = :id")
    void unflagGuest(@Param("id") Integer id);
}