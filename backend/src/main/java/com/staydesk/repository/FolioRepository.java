package com.staydesk.repository;

import com.staydesk.model.Folio;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface FolioRepository extends ListCrudRepository<Folio, Integer> {

    Optional<Folio> getFolioByReservationId(Integer reservationId);
}
