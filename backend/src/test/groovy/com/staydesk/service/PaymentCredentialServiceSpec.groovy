package com.staydesk.service

import com.staydesk.model.Folio
import com.staydesk.model.FolioPayment
import com.staydesk.model.FolioPayment.PaymentKind
import com.staydesk.model.FolioPayment.PaymentStatus
import com.staydesk.model.ReusablePaymentCredential
import com.staydesk.payment.PaymentProvider
import com.staydesk.payment.ReusableCredentialResult
import com.staydesk.provider.ProviderFactory
import com.staydesk.repository.ReusablePaymentCredentialRepository
import spock.lang.Specification

import java.time.LocalDateTime
import java.util.Optional

class PaymentCredentialServiceSpec extends Specification {

    ProviderFactory providerFactory = Mock()
    ReusablePaymentCredentialRepository repository = Mock()

    PaymentCredentialService service = new PaymentCredentialService(providerFactory, repository)

    private static Folio folio() {
        new Folio(1, Folio.FolioStatus.OPEN, BigDecimal.valueOf(75), null, LocalDateTime.now(), LocalDateTime.now())
    }

    private static FolioPayment incidentalsHold() {
        new FolioPayment(5, 1, 10, PaymentKind.INCIDENTALS, "authorizenet", "txn-1", "4242",
                PaymentStatus.REQUIRES_CAPTURE, BigDecimal.valueOf(75), null, "", LocalDateTime.now(), LocalDateTime.now())
    }

    def "saves a credential row when the provider succeeds"() {
        given:
        def provider = Mock(PaymentProvider)
        providerFactory.getProvider("authorizenet") >> provider
        provider.createReusableCredential("txn-1", "folio-1") >>
                new ReusableCredentialResult(true, "cust-1", "profile-1", "4242", null)
        repository.findByFolioIdAndReservationIdAndRevokedFalse(1, 10) >> Optional.empty()

        when:
        service.captureCheckInCredential(folio(), 10, "authorizenet", incidentalsHold())

        then:
        1 * repository.save({ ReusablePaymentCredential c ->
            !c.revoked() && c.expiresAt() == null && c.providerToken() == "profile-1" && c.providerCustomerId() == "cust-1"
        })
    }

    def "swallows a provider failure and saves nothing"() {
        given:
        def provider = Mock(PaymentProvider)
        providerFactory.getProvider("authorizenet") >> provider
        provider.createReusableCredential(*_) >> new ReusableCredentialResult(false, null, null, null, "declined")

        when:
        service.captureCheckInCredential(folio(), 10, "authorizenet", incidentalsHold())

        then:
        noExceptionThrown()
        0 * repository.save(_)
    }

    def "swallows an unexpected exception from the provider and saves nothing"() {
        given:
        def provider = Mock(PaymentProvider)
        providerFactory.getProvider("authorizenet") >> provider
        provider.createReusableCredential(*_) >> { throw new RuntimeException("network error") }

        when:
        service.captureCheckInCredential(folio(), 10, "authorizenet", incidentalsHold())

        then:
        noExceptionThrown()
        0 * repository.save(_)
    }

    def "revokes an existing active credential for the same folio and reservation before saving the new one"() {
        given:
        def provider = Mock(PaymentProvider)
        providerFactory.getProvider("authorizenet") >> provider
        provider.createReusableCredential("txn-1", "folio-1") >>
                new ReusableCredentialResult(true, "cust-2", "profile-2", "4242", null)
        def existing = new ReusablePaymentCredential(7, 1, 10, "authorizenet", "cust-1", "profile-1", "4111",
                false, null, null, LocalDateTime.now(), LocalDateTime.now())
        repository.findByFolioIdAndReservationIdAndRevokedFalse(1, 10) >> Optional.of(existing)

        when:
        service.captureCheckInCredential(folio(), 10, "authorizenet", incidentalsHold())

        then:
        1 * provider.revokeReusableCredential("cust-1", "profile-1")
        1 * repository.markRevoked(7, _ as LocalDateTime)
        1 * repository.save({ ReusablePaymentCredential c -> c.providerToken() == "profile-2" })
    }

    def "marks the old credential revoked locally even when remote revocation fails"() {
        given:
        def provider = Mock(PaymentProvider)
        providerFactory.getProvider("authorizenet") >> provider
        provider.createReusableCredential("txn-1", "folio-1") >>
                new ReusableCredentialResult(true, "cust-2", "profile-2", "4242", null)
        provider.revokeReusableCredential("cust-1", "profile-1") >> { throw new RuntimeException("network error") }
        def existing = new ReusablePaymentCredential(7, 1, 10, "authorizenet", "cust-1", "profile-1", "4111",
                false, null, null, LocalDateTime.now(), LocalDateTime.now())
        repository.findByFolioIdAndReservationIdAndRevokedFalse(1, 10) >> Optional.of(existing)

        when:
        service.captureCheckInCredential(folio(), 10, "authorizenet", incidentalsHold())

        then:
        1 * repository.markRevoked(7, _ as LocalDateTime)
        1 * repository.save(_)
    }

    def "scheduleExpiry delegates to the repository's conditional update"() {
        given:
        def expiry = LocalDateTime.now().plusDays(30)

        when:
        service.scheduleExpiry(1, expiry)

        then:
        1 * repository.scheduleExpiry(1, expiry)
    }
}