package com.staydesk.repository;

import com.staydesk.model.FolioItem;
import org.springframework.data.repository.ListCrudRepository;

public interface FolioItemRepository extends ListCrudRepository<FolioItem, Integer> {
}
