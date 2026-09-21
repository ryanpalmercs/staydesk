package com.staydesk.service

import com.staydesk.model.Folio
import com.staydesk.model.FolioPayment
import com.staydesk.model.FolioPayment.PaymentKind
import com.staydesk.model.FolioPayment.PaymentStatus
import com.staydesk.model.PosDevice
import com.staydesk.model.PropertySetting
import com.staydesk.model.ReusablePaymentCredential
import com.staydesk.payment.AuthResult
import com.staydesk.payment.CaptureResult
import com.staydesk.payment.PaymentProvider
import com.staydesk.payment.RefundResult
import com.staydesk.provider.ProviderFactory
import com.staydesk.repository.FolioPaymentRepository
import com.staydesk.repository.PosDeviceRepository
import com.staydesk.repository.ReusablePaymentCredentialRepository
import spock.lang.Specification

import java.time.LocalDateTime

class PaymentServiceSpec extends Specification {

    ProviderFactory providerFactory = Mock()
    FolioPaymentRepository folioPaymentRepository = Mock()
    PropertySettingsService propertySettingsService = Mock()
    PaymentCredentialService paymentCredentialService = Mock()
    ReusablePaymentCredentialRepository reusablePaymentCredentialRepository = Mock()
    PosDeviceRepository posDeviceRepository = Mock()

    PaymentService paymentService = new PaymentService(providerFactory, folioPaymentRepository, propertySettingsService,
            paymentCredentialService, reusablePaymentCredentialRepository, posDeviceRepository)

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
        provider.chargeStoredCredential(BigDecimal.valueOf(150), "cust-1", "profile-1", "Incident: broken TV", "guest@example.com") >>
                new AuthResult(true, "txn-99", null, "4242")

        when:
        def result = paymentService.chargeStoredCredential(folio, credential, BigDecimal.valueOf(150), "Incident: broken TV", "guest@example.com")

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
        paymentService.chargeStoredCredential(folio, credential, BigDecimal.valueOf(150), "Incident: broken TV", null)

        then:
        thrown(RuntimeException)
        0 * folioPaymentRepository.save(_)
    }

    def "capture settles every INCIDENTALS hold on the folio, not just the first"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(250), null, LocalDateTime.now(), LocalDateTime.now())
        def roomPayment = new FolioPayment(1, 1, null, PaymentKind.ROOM, "authorizenet", "txn-room", "4242",
                PaymentStatus.CAPTURED, BigDecimal.valueOf(150), BigDecimal.valueOf(150), "", LocalDateTime.now(), LocalDateTime.now())
        def incidentals1 = new FolioPayment(2, 1, 10, PaymentKind.INCIDENTALS, "authorizenet", "txn-inc-1", "4242",
                PaymentStatus.REQUIRES_CAPTURE, BigDecimal.valueOf(50), null, "", LocalDateTime.now(), LocalDateTime.now())
        def incidentals2 = new FolioPayment(3, 1, 11, PaymentKind.INCIDENTALS, "authorizenet", "txn-inc-2", "4242",
                PaymentStatus.REQUIRES_CAPTURE, BigDecimal.valueOf(50), null, "", LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        folioPaymentRepository.findByFolioId(1) >> [roomPayment, incidentals1, incidentals2]
        providerFactory.getProvider("authorizenet") >> provider
        folioPaymentRepository.save(_) >> { FolioPayment fp -> fp }

        when:
        def result = paymentService.capture(folio)

        then:
        1 * provider.capture("txn-inc-1", _) >> new CaptureResult(true, "txn-inc-1", null)
        1 * provider.capture("txn-inc-2", _) >> new CaptureResult(true, "txn-inc-2", null)
        result.incidentals().size() == 2
    }

    def "isRoomPaymentSettled is true only when a CAPTURED ROOM payment exists on the folio"() {
        given:
        def capturedRoom = new FolioPayment(1, 5, null, PaymentKind.ROOM, "authorizenet", "txn-1", "4242",
                PaymentStatus.CAPTURED, BigDecimal.valueOf(200), BigDecimal.valueOf(200), "", LocalDateTime.now(), LocalDateTime.now())

        when:
        def settled = paymentService.isRoomPaymentSettled(5)

        then:
        1 * folioPaymentRepository.findByFolioId(5) >> [capturedRoom]
        settled

        when:
        def notSettled = paymentService.isRoomPaymentSettled(6)

        then:
        1 * folioPaymentRepository.findByFolioId(6) >> []
        !notSettled
    }

    private static FolioPayment incidentalsHold(String provider = "authorizenet") {
        new FolioPayment(6, 1, 10, PaymentKind.INCIDENTALS, provider, "hold-1", "4242",
                PaymentStatus.REQUIRES_CAPTURE, BigDecimal.valueOf(100), null, "", LocalDateTime.now(), LocalDateTime.now())
    }

    def "previewCapture returns the real amount owed and recordOnly=false for a real provider"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(300)), incidentalsHold("authorizenet")]

        when:
        def preview = paymentService.previewCapture(folio)

        then:
        preview.amount().compareTo(BigDecimal.valueOf(25)) == 0
        !preview.recordOnly()
    }

    def "previewCapture returns zero amount when the room charge already covers the folio total"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(300)), incidentalsHold("authorizenet")]

        when:
        def preview = paymentService.previewCapture(folio)

        then:
        preview.amount().compareTo(BigDecimal.ZERO) == 0
    }

    def "previewCapture flags recordOnly=true when the incidentals hold is the record-only stand-in, without hiding the amount owed"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(300)), incidentalsHold("elavon_cpi_manual")]

        when:
        def preview = paymentService.previewCapture(folio)

        then:
        preview.amount().compareTo(BigDecimal.valueOf(25)) == 0
        preview.recordOnly()
    }

    def "requiresManualCapture is true when a real provider would capture a non-zero amount"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(300)), incidentalsHold("authorizenet")]

        expect:
        paymentService.requiresManualCapture(folio)
    }

    def "requiresManualCapture is false when the room charge already covers the folio total"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(300)), incidentalsHold("authorizenet")]

        expect:
        !paymentService.requiresManualCapture(folio)
    }

    def "requiresManualCapture is still true when the incidentals hold is the record-only stand-in but a real amount is owed"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> [capturedRoomPayment(BigDecimal.valueOf(300)), incidentalsHold("elavon_cpi_manual")]

        expect:
        paymentService.requiresManualCapture(folio)
    }

    def "requiresManualCapture is true when payment records are missing"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.CLOSED, BigDecimal.valueOf(300), null, LocalDateTime.now(), LocalDateTime.now())
        folioPaymentRepository.findByFolioId(1) >> []

        expect:
        paymentService.requiresManualCapture(folio)
    }

    private static ReusablePaymentCredential activeCredential(String provider = "authorizenet") {
        new ReusablePaymentCredential(1, 1, 10, provider, "cust-1", "profile-1", "4242",
                false, null, null, LocalDateTime.now(), LocalDateTime.now())
    }

    def "chargeExtraToCardOnFile charges the active real credential on file"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(1) >> [activeCredential("authorizenet")]
        providerFactory.getProvider("authorizenet") >> provider
        provider.chargeStoredCredential(BigDecimal.valueOf(25), "cust-1", "profile-1", "Pet Fee", "guest@example.com") >>
                new AuthResult(true, "txn-1", null, "4242")

        when:
        def result = paymentService.chargeExtraToCardOnFile(folio, BigDecimal.valueOf(25), "Pet Fee", "guest@example.com")

        then:
        1 * folioPaymentRepository.save({ FolioPayment fp -> fp.capturedAmount().compareTo(BigDecimal.valueOf(25)) == 0 }) >> { FolioPayment fp -> fp }
        result.capturedAmount().compareTo(BigDecimal.valueOf(25)) == 0
    }

    def "chargeExtraToCardOnFile throws NoReusableCredentialException when the only credential is the record-only stand-in"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())

        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(1) >> [activeCredential("elavon_cpi_manual")]

        when:
        paymentService.chargeExtraToCardOnFile(folio, BigDecimal.valueOf(25), "Pet Fee", null)

        then:
        thrown(com.staydesk.exception.NoReusableCredentialException)
    }

    def "chargeExtraToCardOnFile throws NoReusableCredentialException when there's no active credential"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())

        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(1) >> []

        when:
        paymentService.chargeExtraToCardOnFile(folio, BigDecimal.valueOf(25), "Pet Fee", null)

        then:
        thrown(com.staydesk.exception.NoReusableCredentialException)
    }

    def "chargeExtraTerminal charges the given POS device"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())
        def device = new PosDevice(6, "dev-token-1", "Front Desk", null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        posDeviceRepository.findById(6) >> Optional.of(device)
        providerFactory.getCardPresentProviderName() >> "elavon_cpi"
        providerFactory.getProvider("elavon_cpi") >> provider
        provider.sale(BigDecimal.valueOf(25), "dev-token-1", "Pet Fee", "guest@example.com") >>
                new AuthResult(true, "txn-1", null, "4242")

        when:
        def result = paymentService.chargeExtraTerminal(folio, BigDecimal.valueOf(25), "Pet Fee", 6, "guest@example.com")

        then:
        1 * folioPaymentRepository.save(_) >> { FolioPayment fp -> fp }
        result.capturedAmount().compareTo(BigDecimal.valueOf(25)) == 0
    }

    def "chargeExtraTerminal falls back to record-only recording when no device is given and record-only is enabled"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        providerFactory.isCardPresentRecordOnly() >> true
        providerFactory.getCardPresentProviderName() >> "elavon_cpi_manual"
        providerFactory.getProvider("elavon_cpi_manual") >> provider
        provider.sale(BigDecimal.valueOf(25), "no-device-record-only", "Pet Fee", null) >>
                new AuthResult(true, "MANUAL-1", null, null)

        when:
        paymentService.chargeExtraTerminal(folio, BigDecimal.valueOf(25), "Pet Fee", null, null)

        then:
        0 * posDeviceRepository.findById(_)
        1 * folioPaymentRepository.save(_) >> { FolioPayment fp -> fp }
    }

    def "chargeExtraTerminal throws CardPresentRecordOnlyDisabledException when no device is given and record-only is disabled"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())

        providerFactory.isCardPresentRecordOnly() >> false

        when:
        paymentService.chargeExtraTerminal(folio, BigDecimal.valueOf(25), "Pet Fee", null, null)

        then:
        thrown(com.staydesk.exception.CardPresentRecordOnlyDisabledException)
    }

    def "chargeExtraTerminal throws PosDeviceNotFoundException when the given device doesn't resolve"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(325), null, LocalDateTime.now(), LocalDateTime.now())

        posDeviceRepository.findById(6) >> Optional.empty()

        when:
        paymentService.chargeExtraTerminal(folio, BigDecimal.valueOf(25), "Pet Fee", 6, null)

        then:
        thrown(com.staydesk.exception.PosDeviceNotFoundException)
    }

    private static PropertySetting holdAmountSetting(String value = "0.00") {
        new PropertySetting("incidentals_hold_amount", value, LocalDateTime.now(), LocalDateTime.now())
    }

    def "addCardOnFile places a manual incidentals hold and captures a reusable credential from it"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(80), null, LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        providerFactory.getPaymentProviderName() >> "authorizenet"
        propertySettingsService.getProperty("incidentals_hold_amount") >> holdAmountSetting()
        providerFactory.getProvider("authorizenet") >> provider
        provider.authorize(BigDecimal.ZERO, "manual-token", "INCIDENTALS hold for folio 1", "guest@example.com") >>
                new AuthResult(true, "hold-1", null, "4242")

        when:
        paymentService.addCardOnFile(folio, 10, "manual-token", "guest@example.com")

        then:
        1 * folioPaymentRepository.save({ FolioPayment fp -> fp.kind() == PaymentKind.INCIDENTALS && fp.provider() == "authorizenet" }) >>
                { FolioPayment fp -> fp }
        1 * paymentCredentialService.captureCheckInCredential(folio, 10, "authorizenet", _)
    }

    def "addCardOnFileTerminal charges the given POS device and captures a reusable credential"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(80), null, LocalDateTime.now(), LocalDateTime.now())
        def device = new PosDevice(6, "dev-token-1", "Front Desk", null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        posDeviceRepository.findById(6) >> Optional.of(device)
        providerFactory.getCardPresentProviderName() >> "elavon_cpi"
        propertySettingsService.getProperty("incidentals_hold_amount") >> holdAmountSetting()
        providerFactory.getProvider("elavon_cpi") >> provider
        provider.authorize(BigDecimal.ZERO, "dev-token-1", "INCIDENTALS hold for folio 1", null) >>
                new AuthResult(true, "hold-1", null, "4242")

        when:
        paymentService.addCardOnFileTerminal(folio, 10, 6, null)

        then:
        1 * folioPaymentRepository.save({ FolioPayment fp -> fp.kind() == PaymentKind.INCIDENTALS && fp.provider() == "elavon_cpi" }) >>
                { FolioPayment fp -> fp }
        1 * paymentCredentialService.captureCheckInCredential(folio, 10, "elavon_cpi", _)
    }

    def "addCardOnFileTerminal falls back to record-only recording when no device is given and record-only is enabled"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(80), null, LocalDateTime.now(), LocalDateTime.now())
        def provider = Mock(PaymentProvider)

        providerFactory.isCardPresentRecordOnly() >> true
        providerFactory.getCardPresentProviderName() >> "elavon_cpi_manual"
        propertySettingsService.getProperty("incidentals_hold_amount") >> holdAmountSetting()
        providerFactory.getProvider("elavon_cpi_manual") >> provider
        provider.authorize(BigDecimal.ZERO, "no-device-record-only", "INCIDENTALS hold for folio 1", null) >>
                new AuthResult(true, "MANUAL-1", null, null)

        when:
        paymentService.addCardOnFileTerminal(folio, 10, null, null)

        then:
        0 * posDeviceRepository.findById(_)
        1 * folioPaymentRepository.save(_) >> { FolioPayment fp -> fp }
    }

    def "addCardOnFileTerminal throws CardPresentRecordOnlyDisabledException when no device is given and record-only is disabled"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(80), null, LocalDateTime.now(), LocalDateTime.now())

        providerFactory.isCardPresentRecordOnly() >> false

        when:
        paymentService.addCardOnFileTerminal(folio, 10, null, null)

        then:
        thrown(com.staydesk.exception.CardPresentRecordOnlyDisabledException)
    }

    def "addCardOnFileTerminal throws PosDeviceNotFoundException when the given device doesn't resolve"() {
        given:
        def folio = new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(80), null, LocalDateTime.now(), LocalDateTime.now())

        posDeviceRepository.findById(6) >> Optional.empty()

        when:
        paymentService.addCardOnFileTerminal(folio, 10, 6, null)

        then:
        thrown(com.staydesk.exception.PosDeviceNotFoundException)
    }
}