package com.staydesk.controller;

import com.staydesk.exception.PosDeviceNotFoundException;
import com.staydesk.model.PosDevice;
import com.staydesk.model.PosDeviceHealthResponse;
import com.staydesk.model.request.PairDeviceRequest;
import com.staydesk.payment.elavon.ElavonCpiClient;
import com.staydesk.payment.elavon.dto.CpiDevice;
import com.staydesk.repository.PosDeviceRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/pos-devices")
public class PosDeviceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PosDeviceController.class);

    private final PosDeviceRepository posDeviceRepository;
    private final ElavonCpiClient elavonCpiClient;

    public PosDeviceController(PosDeviceRepository posDeviceRepository, ElavonCpiClient elavonCpiClient) {
        this.posDeviceRepository = posDeviceRepository;
        this.elavonCpiClient = elavonCpiClient;
    }

    @GetMapping
    public List<PosDevice> getDevices() {
        return posDeviceRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<PosDevice> pairDevice(@Valid @RequestBody PairDeviceRequest request) {
        LOGGER.info("Pairing POS device with friendly name {}", request.friendlyName());

        CpiDevice cpiDevice = elavonCpiClient.pairDevice(request.pairingCode(), request.friendlyName(), request.location());

        LocalDateTime now = LocalDateTime.now();
        PosDevice saved = posDeviceRepository.save(new PosDevice(0, cpiDevice.deviceIdentifiers().deviceId(),
                request.friendlyName(), request.location(), now, now, now));

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> unpairDevice(@PathVariable int id) {
        PosDevice device = posDeviceRepository.findById(id).orElseThrow(PosDeviceNotFoundException::new);

        elavonCpiClient.unpairDevice(device.deviceId());
        posDeviceRepository.deleteById(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("{id}/health-check")
    public ResponseEntity<PosDeviceHealthResponse> healthCheck(@PathVariable int id) {
        PosDevice device = posDeviceRepository.findById(id).orElseThrow(PosDeviceNotFoundException::new);

        boolean online = elavonCpiClient.healthCheck(device.deviceId());

        return ResponseEntity.ok(new PosDeviceHealthResponse(online));
    }
}