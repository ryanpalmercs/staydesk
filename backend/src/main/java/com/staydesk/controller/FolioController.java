package com.staydesk.controller;

import com.staydesk.exception.ExtraNotFoundException;
import com.staydesk.exception.FolioClosedException;
import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.exception.FolioPaymentNotFoundException;
import com.staydesk.model.request.AddFolioItemRequest;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioItem;
import com.staydesk.repository.FolioItemRepository;
import com.staydesk.repository.FolioRepository;
import com.staydesk.service.FolioService;
import com.staydesk.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/folios")
public class FolioController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FolioController.class);

    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;
    private final PaymentService paymentService;
    private final FolioService folioService;

    public FolioController(FolioRepository folioRepository, FolioItemRepository folioItemRepository,
                           PaymentService paymentService, FolioService folioService) {
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
        this.paymentService = paymentService;
        this.folioService = folioService;
    }

    @GetMapping("{id}")
    public ResponseEntity<Folio> getFolio(@PathVariable Integer id) {
        return folioRepository.findById(id)
                              .map(ResponseEntity::ok)
                              .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("{id}/items")
    public ResponseEntity<List<FolioItem>> getFolioItems(@PathVariable Integer id) {
        if (folioRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(folioItemRepository.findByFolioId(id));
    }

    @GetMapping("by-reservation/{reservationId}")
    public ResponseEntity<Folio> getByReservation(@PathVariable Integer reservationId) {
        return folioRepository.getFolioByReservationId(reservationId)
                              .map(ResponseEntity::ok)
                              .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("{id}/items")
    public ResponseEntity<Folio> addItem(@PathVariable Integer id, @RequestBody AddFolioItemRequest request) {
        LOGGER.info("Adding extra {} x{} to folio {}", request.extraId(), request.quantity(), id);

        try {
            return ResponseEntity.ok(folioService.addExtra(id, request.extraId(), request.quantity()));
        } catch (FolioNotFoundException | ExtraNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FolioClosedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
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