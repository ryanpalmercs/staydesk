package com.staydesk.controller;

import com.staydesk.model.Reservation;
import com.staydesk.model.dto.RemoteCheckInSummary;
import com.staydesk.model.request.RemoteCheckInCompleteRequest;
import com.staydesk.service.RemoteCheckInService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/remote-check-in")
public class RemoteCheckInController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCheckInController.class);

    private final RemoteCheckInService remoteCheckInService;

    public RemoteCheckInController(RemoteCheckInService remoteCheckInService) {
        this.remoteCheckInService = remoteCheckInService;
    }

    @GetMapping("{token}")
    public RemoteCheckInSummary getSummary(@PathVariable String token) {
        return remoteCheckInService.getSummaryForToken(token);
    }

    @PostMapping("{token}/complete")
    public Reservation complete(@PathVariable String token, @Valid @RequestBody RemoteCheckInCompleteRequest request) {
        LOGGER.info("Completing remote check-in");
        return remoteCheckInService.completeRemoteCheckIn(token, request.incidentalsPaymentMethodId(), request.agreedToTerms());
    }
}
