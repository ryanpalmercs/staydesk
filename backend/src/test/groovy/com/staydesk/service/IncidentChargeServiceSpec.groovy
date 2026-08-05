package com.staydesk.service
import com.staydesk.exception.FolioNotClosedException
import com.staydesk.exception.IncidentChargeAlreadyDecidedException
import com.staydesk.exception.NoReusableCredentialException
import com.staydesk.model.Folio
import com.staydesk.model.FolioPayment
import com.staydesk.model.FolioPayment.PaymentKind
import com.staydesk.model.FolioPayment.PaymentStatus
import com.staydesk.model.IncidentChargeRequest
import com.staydesk.model.IncidentChargeRequest.IncidentChargeStatus
import com.staydesk.model.ReusablePaymentCredential
import com.staydesk.repository.FolioRepository
import com.staydesk.repository.IncidentChargeRequestRepository
import com.staydesk.repository.ReusablePaymentCredentialRepository
import spock.lang.Specification

import java.time.LocalDateTime

class IncidentChargeServiceSpec extends Specification {

    IncidentChargeRequestRepository incidentChargeRequestRepository = Mock()
    ReusablePaymentCredentialRepository reusablePaymentCredentialRepository = Mock()
    FolioRepository folioRepository = Mock()
    PaymentService paymentService = Mock()
    FolioService folioService = Mock()

    IncidentChargeService service = new IncidentChargeService(incidentChargeRequestRepository,
            reusablePaymentCredentialRepository, folioRepository, paymentService, folioService)

    def staffId = UUID.randomUUID()

    private static Folio closedFolio() {
        new Folio(1, 10, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(150), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())
    }

    private static ReusablePaymentCredential activeCredential() {
        new ReusablePaymentCredential(1, 1, 10, "authorizenet", "cust-1", "profile-1", "4242",
                false, null, LocalDateTime.now().plusDays(10), LocalDateTime.now(), LocalDateTime.now())
    }

    private static IncidentChargeRequest pendingRequest() {
        new IncidentChargeRequest(1, 1, 1, BigDecimal.valueOf(150), "Broken TV", IncidentChargeRequest.IncidentChargeStatus.PENDING,
                UUID.randomUUID(), LocalDateTime.now(), null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now())
    }

    def "requestCharge throws FolioNotClosedException for an OPEN folio"() {
        given:
        def openFolio = new Folio(1, 10, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        folioRepository.findById(1) >> Optional.of(openFolio)

        when:
        service.requestCharge(1, BigDecimal.valueOf(150), "Broken TV", staffId)

        then:
        thrown(FolioNotClosedException)
        0 * incidentChargeRequestRepository.save(_)
    }

    def "requestCharge throws NoReusableCredentialException when no active credential exists"() {
        given:
        folioRepository.findById(1) >> Optional.of(closedFolio())
        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(1) >> []

        when:
        service.requestCharge(1, BigDecimal.valueOf(150), "Broken TV", staffId)

        then:
        thrown(NoReusableCredentialException)
    }

    def "requestCharge throws NoReusableCredentialException when the only credential is expired"() {
        given:
        def expired = new ReusablePaymentCredential(1, 1, 10, "authorizenet", "cust-1", "profile-1", "4242",
                false, null, LocalDateTime.now().minusDays(1), LocalDateTime.now(), LocalDateTime.now())
        folioRepository.findById(1) >> Optional.of(closedFolio())
        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(1) >> [expired]

        when:
        service.requestCharge(1, BigDecimal.valueOf(150), "Broken TV", staffId)

        then:
        thrown(NoReusableCredentialException)
    }

    def "requestCharge creates a PENDING request against the active credential"() {
        given:
        folioRepository.findById(1) >> Optional.of(closedFolio())
        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(1) >> [activeCredential()]
        incidentChargeRequestRepository.save(_) >> { IncidentChargeRequest r -> r }

        when:
        def result = service.requestCharge(1, BigDecimal.valueOf(150), "Broken TV", staffId)

        then:
        result.status() == IncidentChargeStatus.PENDING
        result.reusablePaymentCredentialId() == 1
        result.requestedBy() == staffId
    }

    def "approve throws IncidentChargeAlreadyDecidedException for a non-PENDING request"() {
        given:
        def decided = new IncidentChargeRequest(1, 1, 1, BigDecimal.valueOf(150), "Broken TV", IncidentChargeStatus.CHARGED,
                UUID.randomUUID(), LocalDateTime.now(), staffId, LocalDateTime.now(), null, 5, null,
                LocalDateTime.now(), LocalDateTime.now())
        incidentChargeRequestRepository.findById(1) >> Optional.of(decided)

        when:
        service.approve(1, staffId)

        then:
        thrown(IncidentChargeAlreadyDecidedException)
        0 * paymentService.chargeStoredCredential(*_)
    }

    def "approve charges the credential, posts to the folio, and marks CHARGED on success"() {
        given:
        incidentChargeRequestRepository.findById(1) >> Optional.of(pendingRequest())
        reusablePaymentCredentialRepository.findById(1) >> Optional.of(activeCredential())
        folioRepository.findById(1) >> Optional.of(closedFolio())

        def savedPayment = new FolioPayment(9, 1, PaymentKind.INCIDENT_CHARGE, "authorizenet", "txn-1", "4242",
                PaymentStatus.CAPTURED, BigDecimal.valueOf(150), BigDecimal.valueOf(150), "", LocalDateTime.now(), LocalDateTime.now())
        paymentService.chargeStoredCredential(_, _, _, _) >> savedPayment
        incidentChargeRequestRepository.save(_) >> { IncidentChargeRequest r -> r }

        when:
        def result = service.approve(1, staffId)

        then:
        1 * folioService.postIncidentCharge(_, _, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(150)) == 0 })
        result.status() == IncidentChargeStatus.CHARGED
        result.folioPaymentId() == 9
        result.approvedBy() == staffId
    }

    def "approve marks FAILED and leaves the folio untouched when the provider charge throws"() {
        given:
        incidentChargeRequestRepository.findById(1) >> Optional.of(pendingRequest())
        reusablePaymentCredentialRepository.findById(1) >> Optional.of(activeCredential())
        folioRepository.findById(1) >> Optional.of(closedFolio())

        paymentService.chargeStoredCredential(_, _, _, _) >> { throw new RuntimeException("declined") }
        incidentChargeRequestRepository.save(_) >> { IncidentChargeRequest r -> r }

        when:
        def result = service.approve(1, staffId)

        then:
        0 * folioService.postIncidentCharge(*_)
        result.status() == IncidentChargeStatus.FAILED
        result.failureReason() == "declined"
    }

    def "reject transitions a PENDING request to REJECTED without touching payment or folio"() {
        given:
        incidentChargeRequestRepository.findById(1) >> Optional.of(pendingRequest())
        incidentChargeRequestRepository.save(_) >> { IncidentChargeRequest r -> r }

        when:
        def result = service.reject(1, staffId, "Not a valid claim")

        then:
        0 * paymentService.chargeStoredCredential(*_)
        0 * folioService.postIncidentCharge(*_)
        result.status() == IncidentChargeStatus.REJECTED
        result.rejectionReason() == "Not a valid claim"
    }

    def "reject throws IncidentChargeAlreadyDecidedException for a non-PENDING request"() {
        given:
        def decided = new IncidentChargeRequest(1, 1, 1, BigDecimal.valueOf(150), "Broken TV", IncidentChargeStatus.REJECTED,
                UUID.randomUUID(), LocalDateTime.now(), staffId, LocalDateTime.now(), "already handled", null, null,
                LocalDateTime.now(), LocalDateTime.now())
        incidentChargeRequestRepository.findById(1) >> Optional.of(decided)

        when:
        service.reject(1, staffId, "again")

        then:
        thrown(IncidentChargeAlreadyDecidedException)
    }
}
