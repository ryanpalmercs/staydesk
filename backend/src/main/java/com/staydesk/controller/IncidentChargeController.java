package com.staydesk.controller;

import com.staydesk.model.IncidentChargeRequest;
import com.staydesk.model.request.IncidentChargeRequestBody;
import com.staydesk.model.request.RejectIncidentChargeRequest;
import com.staydesk.service.IncidentChargeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class IncidentChargeController {

    private final IncidentChargeService incidentChargeService;

    public IncidentChargeController(IncidentChargeService incidentChargeService) {
        this.incidentChargeService = incidentChargeService;
    }

    @PostMapping("/folios/{id}/incident-charges")
    public ResponseEntity<IncidentChargeRequest> requestCharge(@PathVariable Integer id,
                                                               @RequestBody IncidentChargeRequestBody request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(incidentChargeService.requestCharge(id, request.amount(), request.reason(),
                UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/folios/{id}/incident-charges")
    public ResponseEntity<List<IncidentChargeRequest>> listForFolio(@PathVariable Integer id) {
        return ResponseEntity.ok(incidentChargeService.listByFolio(id));
    }

    @GetMapping("/incident-charges/pending")
    public ResponseEntity<List<IncidentChargeRequest>> listPending() {
        return ResponseEntity.ok(incidentChargeService.listPending());
    }

    @PostMapping("/incident-charges/{id}/approve")
    public ResponseEntity<IncidentChargeRequest> approve(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(incidentChargeService.approve(id, UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/incident-charges/{id}/reject")
    public ResponseEntity<IncidentChargeRequest> reject(@PathVariable Integer id,
                                                        @RequestBody RejectIncidentChargeRequest request,
                                                        @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(incidentChargeService.reject(id, UUID.fromString(jwt.getSubject()), request.reason()));
    }
}