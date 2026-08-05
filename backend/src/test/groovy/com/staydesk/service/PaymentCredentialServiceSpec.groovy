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

class PaymentCredentialServiceSpec extends Specification {

    ProviderFactory providerFactory = Mock()
    ReusablePaymentCredentialRepository repository = Mock()

    PaymentCredentialService service = new PaymentCredentialService(providerFactory, repository)

    private static Folio folio() {
        new Folio(1, 10, Folio.FolioStatus.OPEN, BigDecimal.valueOf(75), null, LocalDateTime.now(), LocalDateTime.now())
    }

    private static FolioPayment incidentalsHold() {
        new FolioPayment(5, 1, PaymentKind.INCIDENTALS, "authorizenet", "txn-1", "4242",
                PaymentStatus.REQUIRES_CAPTURE, BigDecimal.valueOf(75), null, "", LocalDateTime.now(), LocalDateTime.now())
    }

    def "saves a credential row when the provider succeeds"() {
        given:
        def provider = Mock(PaymentProvider)
        providerFactory.getProvider("authorizenet") >> provider
        provider.createReusableCredential("txn-1", "folio-1") >>
                new ReusableCredentialResult(true, "cust-1", "profile-1", "4242", null)

        when:
        service.captureCheckInCredential(folio(), "authorizenet", incidentalsHold())

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
        service.captureCheckInCredential(folio(), "authorizenet", incidentalsHold())

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
        service.captureCheckInCredential(folio(), "authorizenet", incidentalsHold())

        then:
        noExceptionThrown()
        0 * repository.save(_)
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