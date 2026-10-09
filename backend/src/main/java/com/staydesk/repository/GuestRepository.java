package com.staydesk.repository;

import com.staydesk.model.Guest;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestRepository extends ListCrudRepository<Guest, Integer> {

    // email_hash has a UNIQUE constraint (V15), so this is safely a single-result lookup.
    Optional<Guest> findByEmailHash(String emailHash);

    // phone_hash has no such constraint -- guests can legitimately share a phone number (e.g.
    // family members booking separately), so this can return more than one row.
    @Query("SELECT * FROM guests WHERE phone_hash = :phoneHash")
    List<Guest> findByPhoneHash(@Param("phoneHash") String phoneHash);

    @Modifying
    @Query("UPDATE guests SET phone_hash = :phoneHash WHERE id = :id")
    void updatePhoneHash(@Param("id") Integer id, @Param("phoneHash") String phoneHash);

    @Modifying
    @Query("UPDATE guests SET flagged = TRUE, flag_reason = :reason, flagged_date = now(), flagged_by = :flaggedBy WHERE id = :id")
    void flagGuest(@Param("id") Integer id, @Param("reason") String reason, @Param("flaggedBy") UUID flaggedBy);

    @Modifying
    @Query("UPDATE guests SET flagged = FALSE, flag_reason = NULL, flagged_date = NULL, flagged_by = NULL WHERE id = :id")
    void unflagGuest(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE guests SET legal_hold = TRUE WHERE id = :id")
    void setLegalHold(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE guests SET legal_hold = FALSE WHERE id = :id")
    void clearLegalHold(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE guests SET notes = :notes WHERE id = :id")
    void setNotes(@Param("id") Integer id, @Param("notes") String notes);
}