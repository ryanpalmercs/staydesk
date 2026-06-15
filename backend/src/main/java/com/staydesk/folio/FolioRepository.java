package com.staydesk.folio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FolioRepository extends JpaRepository<Folio, UUID> {
}
