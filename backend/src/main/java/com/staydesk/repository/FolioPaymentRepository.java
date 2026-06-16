package com.staydesk.repository;

import com.staydesk.model.FolioPayment;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface FolioPaymentRepository extends ListCrudRepository<FolioPayment, Integer> {

    List<FolioPayment> findByFolioId(Integer folioId);
}