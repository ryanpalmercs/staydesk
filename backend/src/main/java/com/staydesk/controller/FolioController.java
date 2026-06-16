package com.staydesk.controller;

import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.exception.FolioPaymentNotFoundException;
import com.staydesk.model.Folio;
import com.staydesk.repository.FolioRepository;
import com.staydesk.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/folios")
public class FolioController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FolioController.class);

    private final FolioRepository folioRepository;
    private final PaymentService paymentService;

    public FolioController(FolioRepository folioRepository, PaymentService paymentService) {
        this.folioRepository = folioRepository;
        this.paymentService = paymentService;
    }

    @GetMapping("by-reservation/{reservationId}")
    public ResponseEntity<Folio> getByReservation(@PathVariable Integer reservationId) {
        return folioRepository.getFolioByReservationId(reservationId)
                              .map(ResponseEntity::ok)
                              .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("{id}/pay")
    public ResponseEntity<PaymentService.PaymentCaptureResult> pay(@PathVariable Integer id) {
        LOGGER.info("Capturing payment for folio {}", id);

        try {
            Folio folio = folioRepository.findById(id).orElseThrow(FolioNotFoundException::new);
            PaymentService.PaymentCaptureResult result = paymentService.capture(folio);

            folioRepository.save(new Folio(folio.id(), folio.reservationId(), folio.status(), folio.total(),
                    LocalDateTime.now(), folio.createdAt(), folio.updatedAt()));

            return ResponseEntity.ok(result);
        } catch (FolioNotFoundException | FolioPaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            LOGGER.error("Failed to capture payment for folio {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}