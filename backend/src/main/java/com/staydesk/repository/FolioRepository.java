package com.staydesk.repository;

import com.staydesk.model.Folio;
import org.springframework.data.repository.ListCrudRepository;

public interface FolioRepository extends ListCrudRepository<Folio, Integer> {
}
