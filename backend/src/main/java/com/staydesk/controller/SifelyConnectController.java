package com.staydesk.controller;

import com.staydesk.model.SifelyStatusResponse;
import com.staydesk.model.request.SifelyConnectRequest;
import com.staydesk.service.SifelyAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sifely")
public class SifelyConnectController {

    private final SifelyAuthService sifelyAuthService;

    public SifelyConnectController(SifelyAuthService sifelyAuthService) {
        this.sifelyAuthService = sifelyAuthService;
    }

    @PostMapping("connect")
    public ResponseEntity<Void> connect(@RequestBody SifelyConnectRequest request) {
        sifelyAuthService.connect(request.account(), request.password(), request.clientId(), request.clientSecret());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("status")
    public ResponseEntity<SifelyStatusResponse> getStatus() {
        return ResponseEntity.ok(sifelyAuthService.getStatus());
    }

    @DeleteMapping("connect")
    public ResponseEntity<Void> disconnect() {
        sifelyAuthService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
