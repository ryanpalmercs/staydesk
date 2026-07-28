package com.staydesk.payment.elavon

import com.staydesk.payment.ReusableCredentialResult
import com.staydesk.payment.elavon.dto.CpiResponseFields
import com.staydesk.payment.elavon.dto.CpiSafetyFields
import com.staydesk.payment.elavon.dto.CpiToken
import com.staydesk.payment.elavon.dto.CpiTransaction
import spock.lang.Specification

class ElavonCpiPaymentProviderSpec extends Specification {

    ElavonCpiClient client = Mock()

    ElavonCpiPaymentProvider provider = new ElavonCpiPaymentProvider(client)

    def "authorize flags the transaction as ad-hoc credential-on-file establishing"() {
        given:
        client.referenceNumber() >> "ref-1"

        when:
        provider.authorize(BigDecimal.valueOf(75), "device-1", "Incidentals hold")

        then:
        1 * client.sendDeviceMessage("device-1", { CpiTransaction req -> req.cardTransIdentifierIndicator() == "F101" }) >>
                new CpiTransaction("ref-1", "AUTH", "75", null, null,
                        new CpiSafetyFields(new CpiToken("token-1")), null,
                        new CpiResponseFields(null, "0000", "APPROVED", null, null, null), null)
    }

    def "chargeStoredCredential sends a merchant-initiated SALE via the gateway with the stored token"() {
        given:
        client.referenceNumber() >> "ref-2"

        when:
        def result = provider.chargeStoredCredential(BigDecimal.valueOf(150), null, "stored-token-1", "Incident: broken TV")

        then:
        1 * client.sendGatewayMessage({ CpiTransaction req ->
            req.transType() == "SALE" &&
                    req.cardTransIdentifierIndicator() == "M206" &&
                    req.safetyFields().tokenization().token() == "stored-token-1"
        }) >> new CpiTransaction("ref-2", "SALE", "150", null, null,
                new CpiSafetyFields(new CpiToken("stored-token-1")), null,
                new CpiResponseFields(null, "0000", "APPROVED", null, null, null), null)

        result.success()
        result.transactionId() == "stored-token-1"
    }

    def "chargeStoredCredential returns a failed AuthResult when CPI declines"() {
        given:
        client.referenceNumber() >> "ref-3"
        client.sendGatewayMessage(_) >> new CpiTransaction("ref-3", "SALE", "150", null, null,
                null, null, new CpiResponseFields(null, "0100", "DECLINED", null, null, null), null)

        when:
        def result = provider.chargeStoredCredential(BigDecimal.valueOf(150), null, "stored-token-1", "Incident")

        then:
        !result.success()
        result.message() == "DECLINED"
    }

    def "createReusableCredential is a pure passthrough with no client interaction"() {
        when:
        ReusableCredentialResult result = provider.createReusableCredential("token-from-checkin", "folio-1")

        then:
        0 * client._
        result.success()
        result.providerToken() == "token-from-checkin"
        result.providerCustomerId() == null
    }

    def "revokeReusableCredential is a no-op"() {
        when:
        provider.revokeReusableCredential(null, "stored-token-1")

        then:
        0 * client._
    }
}