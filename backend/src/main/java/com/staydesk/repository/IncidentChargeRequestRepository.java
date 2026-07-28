package com.staydesk.repository;

import com.staydesk.model.IncidentChargeRequest;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface IncidentChargeRequestRepository extends ListCrudRepository<IncidentChargeRequest, Integer> {

    List<IncidentChargeRequest> findByFolioId(int folioId);

    List<IncidentChargeRequest> findByStatus(IncidentChargeRequest.IncidentChargeStatus status);
}