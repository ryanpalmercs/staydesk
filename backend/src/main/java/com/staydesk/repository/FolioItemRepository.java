package com.staydesk.repository;

import com.staydesk.model.FolioItem;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface FolioItemRepository extends ListCrudRepository<FolioItem, Integer> {

    List<FolioItem> findByFolioId(Integer folioId);
}
