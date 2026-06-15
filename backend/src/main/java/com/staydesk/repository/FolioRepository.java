package com.staydesk.repository;

import com.staydesk.model.Folio;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FolioRepository extends ListCrudRepository<Folio, Integer> {

    Optional<Folio> getFolioByReservationId(Integer reservationId);

    @Modifying
    @Query("UPDATE folios SET status = 'CLOSED' WHERE id = :id")
    void closeFolio(@Param("id") Integer id);
}
