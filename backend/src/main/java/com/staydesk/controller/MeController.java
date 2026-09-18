package com.staydesk.controller;

import com.staydesk.model.dto.CurrentUserResponse;
import com.staydesk.model.request.AcknowledgeVersionRequest;
import com.staydesk.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class MeController {

    private final CurrentUserService currentUserService;

    public MeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(currentUserService.getCurrentUser(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/me/acknowledge-version")
    public ResponseEntity<Void> acknowledgeVersion(@AuthenticationPrincipal Jwt jwt, @RequestBody AcknowledgeVersionRequest request) {
        currentUserService.acknowledgeVersion(UUID.fromString(jwt.getSubject()), request.releaseNotesId());
        return ResponseEntity.noContent().build();
    }
}
