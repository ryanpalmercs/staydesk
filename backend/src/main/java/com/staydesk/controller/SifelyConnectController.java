package com.staydesk.controller;

import com.staydesk.service.SifelyAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sifely")
public class SifelyConnectController {

    private final SifelyAuthService sifelyAuthService;

    public SifelyConnectController(SifelyAuthService sifelyAuthService) {
        this.sifelyAuthService = sifelyAuthService;
    }

    @PostMapping("connect")
    public ResponseEntity<Void> connect(@RequestParam String account, @RequestParam String password,
                                        @RequestParam String clientId, @RequestParam String clientSecret) {
        sifelyAuthService.connect(account, password, clientId, clientSecret);
        return ResponseEntity.noContent().build();
    }
}