package com.staydesk.service

import com.staydesk.model.Folio
import com.staydesk.model.FolioPayment
import com.staydesk.model.FolioPayment.PaymentKind
import com.staydesk.model.FolioPayment.PaymentStatus
import com.staydesk.model.ReusablePaymentCredential
import com.staydesk.payment.AuthResult
import com.staydesk.payment.PaymentProvider
import com.staydesk.payment.RefundResult
import com.staydesk.provider.ProviderFactory
import com.staydesk.repository.FolioPaymentRepository
import spock.lang.Specification

import java.time.LocalDateTime

class PaymentServiceSpec extends Specification {

    ProviderFactory providerFactory = Mock()
    FolioPaymentRepository folioPaymentRepository = Mock()
    PropertySettingsService propertySettingsService = Mock()
    PaymentCredentialService paymentCredentialService = Mock()

    PaymentService paymentService = new PaymentService(providerFactory, folioPaymentRepository, propertySettingsService,
            paymentCredentialService)

    private static FolioPayment capturedRoomPayment(BigDecimal amount) {
        new FolioPayment(5, 1, null, PaymentKind.ROOM, "authorizenet", "txn-1", "4242",
                PaymentStatus.CAPTURED, amount, amount, "", LocalDateTime.now(), LocalDateTime.now())
    }

    def "refunds captured amount minus retained amount and marks PARTIALLY_REFUNDED"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        def roomPayment = capturedRoomPayment(BigDecimal.valueOf(300))
        def provider = Mock(PaymentProvider)

        folioPaymentRepository.findByFolioId(1) >> [roomPayment]
        providerFactory.getProvider("authorizenet") >> provider

        when:
        paymentService.refundReservationShare(folio, BigDecimal.valueOf(300), BigDecimal.valueOf(100))

        then:
        1 * provider.refund("txn-1", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(200)) == 0 }, "4242") >>
                new RefundResult(true, "txn-1", "ok")
        1 * folioPaymentRepository.save({ FolioPayment saved ->
            saved.status() == PaymentStatus.PARTIALLY_REFUNDED && saved.capturedAmount().compareTo(BigDecimal.valueOf(100)) == 0
        })
    }

    def "does not call refund when retained amount consumes the full captured amount"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(80), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(80))]

        when:
        paymentService.refundReservationShare(folio, BigDecimal.valueOf(80), BigDecimal.valueOf(100))

        then:
        0 * providerFactory.getProvider(_)
        0 * folioPaymentRepository.save(_)
    }

    def "throws when the provider declines the refund"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(300))]
        providerFactory.getProvider("authorizenet") >> provider
        provider.refund(*_) >> new RefundResult(false, null, "declined")

        when:
        paymentService.refundReservationShare(folio, BigDecimal.valueOf(300), BigDecimal.valueOf(100))

        then:
        thrown(RuntimeException)
        0 * folioPaymentRepository.save(_)
    }

    def "does nothing when there's no captured ROOM payment yet"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> []

        when:
        paymentService.refundReservationShare(folio, BigDecimal.valueOf(300), BigDecimal.valueOf(100))

        then:
        noExceptionThrown()
        0 * providerFactory.getProvider(_)
        0 * folioPaymentRepository.save(_)
    }

    def "chargeStoredCredential saves an INCIDENT_CHARGE FolioPayment on success"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        def credential = new ReusablePaymentCredential(1, 1, 10, "authorizenet", "cust-1", "profile-1", "4242",
                false, null, null, LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        providerFactory.getProvider("authorizenet") >> provider
        provider.chargeStoredCredential(BigDecimal.valueOf(150), "cust-1", "profile-1", "Incident: broken TV") >>
                new AuthResult(true, "txn-99", null, "4242")

        when:
        def result = paymentService.chargeStoredCredential(folio, credential, BigDecimal.valueOf(150), "Incident: broken TV")

        then:
        1 * folioPaymentRepository.save({ FolioPayment saved ->
            saved.kind() == PaymentKind.INCIDENT_CHARGE &&
                    saved.status() == PaymentStatus.CAPTURED &&
                    saved.capturedAmount().compareTo(BigDecimal.valueOf(150)) == 0
        }) >> { FolioPayment fp -> fp }
        result.kind() == PaymentKind.INCIDENT_CHARGE
    }

    def "chargeStoredCredential throws and saves nothing when the provider declines"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        def credential = new ReusablePaymentCredential(1, 1, 10, "authorizenet", "cust-1", "profile-1", "4242",
                false, null, null, LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        providerFactory.getProvider("authorizenet") >> provider
        provider.chargeStoredCredential(*_) >> new AuthResult(false, null, "declined", null)

        when:
        paymentService.chargeStoredCredential(folio, credential, BigDecimal.valueOf(150), "Incident: broken TV")

        then:
        thrown(RuntimeException)
        0 * folioPaymentRepository.save(_)
    }
}