package com.staydesk.service;

import com.staydesk.exception.FolioNotClosedException;
import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.exception.IncidentChargeAlreadyDecidedException;
import com.staydesk.exception.IncidentChargeRequestNotFoundException;
import com.staydesk.exception.NoReusableCredentialException;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioPayment;
import com.staydesk.model.IncidentChargeRequest;
import com.staydesk.model.IncidentChargeRequest.IncidentChargeStatus;
import com.staydesk.model.ReusablePaymentCredential;
import com.staydesk.repository.FolioRepository;
import com.staydesk.repository.IncidentChargeRequestRepository;
import com.staydesk.repository.ReusablePaymentCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentChargeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentChargeService.class);

    private final IncidentChargeRequestRepository incidentChargeRequestRepository;
    private final ReusablePaymentCredentialRepository reusablePaymentCredentialRepository;
    private final FolioRepository folioRepository;
    private final PaymentService paymentService;
    private final FolioService folioService;

    public IncidentChargeService(IncidentChargeRequestRepository incidentChargeRequestRepository,
                                 ReusablePaymentCredentialRepository reusablePaymentCredentialRepository,
                                 FolioRepository folioRepository, PaymentService paymentService,
                                 FolioService folioService) {
        this.incidentChargeRequestRepository = incidentChargeRequestRepository;
        this.reusablePaymentCredentialRepository = reusablePaymentCredentialRepository;
        this.folioRepository = folioRepository;
        this.paymentService = paymentService;
        this.folioService = folioService;
    }

    @Transactional
    public IncidentChargeRequest requestCharge(int folioId, BigDecimal amount, String reason, UUID requestedBy) {
        Folio folio = folioRepository.findById(folioId).orElseThrow(FolioNotFoundException::new);

        if (folio.status() != Folio.FolioStatus.CLOSED) {
            throw new FolioNotClosedException();
        }

        LocalDateTime now = LocalDateTime.now();

        ReusablePaymentCredential credential = reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(folioId)
                .stream()
                .filter(c -> c.expiresAt() == null || c.expiresAt().isAfter(now))
                .findFirst()
                .orElseThrow(NoReusableCredentialException::new);

        return incidentChargeRequestRepository.save(new IncidentChargeRequest(0, folioId, credential.id(), amount, reason,
                IncidentChargeStatus.PENDING, requestedBy, now, null, null, null, null, null, now, now));
    }

    @Transactional
    public IncidentChargeRequest approve(int requestId, UUID approvedBy) {
        IncidentChargeRequest request = incidentChargeRequestRepository.findById(requestId)
                                                                       .orElseThrow(IncidentChargeRequestNotFoundException::new);

        if (request.status() != IncidentChargeStatus.PENDING) {
            throw new IncidentChargeAlreadyDecidedException();
        }

        ReusablePaymentCredential credential = reusablePaymentCredentialRepository.findById(request.reusablePaymentCredentialId())
                                                                                  .orElseThrow(NoReusableCredentialException::new);
        Folio folio = folioRepository.findById(request.folioId()).orElseThrow(FolioNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        String description = "Incident: " + request.reason();

        try {
            FolioPayment payment = paymentService.chargeStoredCredential(folio, credential, request.amount(), description);
            folioService.postIncidentCharge(folio, description, request.amount());

            return incidentChargeRequestRepository.save(new IncidentChargeRequest(request.id(), request.folioId(),
                    request.reusablePaymentCredentialId(), request.amount(), request.reason(), IncidentChargeStatus.CHARGED,
                    request.requestedBy(), request.requestedAt(), approvedBy, now, request.rejectionReason(),
                    payment.id(), request.failureReason(), request.createdAt(), now));
        } catch (RuntimeException e) {
            LOGGER.error("Failed to charge incident request {} for folio {}", requestId, request.folioId(), e);

            return incidentChargeRequestRepository.save(new IncidentChargeRequest(request.id(), request.folioId(),
                    request.reusablePaymentCredentialId(), request.amount(), request.reason(), IncidentChargeStatus.FAILED,
                    request.requestedBy(), request.requestedAt(), approvedBy, now, request.rejectionReason(),
                    request.folioPaymentId(), e.getMessage(), request.createdAt(), now));
        }
    }

    @Transactional
    public IncidentChargeRequest reject(int requestId, UUID approvedBy, String rejectionReason) {
        IncidentChargeRequest request = incidentChargeRequestRepository.findById(requestId)
                                                                       .orElseThrow(IncidentChargeRequestNotFoundException::new);

        if (request.status() != IncidentChargeStatus.PENDING) {
            throw new IncidentChargeAlreadyDecidedException();
        }

        LocalDateTime now = LocalDateTime.now();

        return incidentChargeRequestRepository.save(new IncidentChargeRequest(request.id(), request.folioId(),
                request.reusablePaymentCredentialId(), request.amount(), request.reason(), IncidentChargeStatus.REJECTED,
                request.requestedBy(), request.requestedAt(), approvedBy, now, rejectionReason, request.folioPaymentId(),
                request.failureReason(), request.createdAt(), now));
    }

    public List<IncidentChargeRequest> listPending() {
        return incidentChargeRequestRepository.findByStatus(IncidentChargeStatus.PENDING);
    }

    public List<IncidentChargeRequest> listByFolio(int folioId) {
        return incidentChargeRequestRepository.findByFolioId(folioId);
    }
}