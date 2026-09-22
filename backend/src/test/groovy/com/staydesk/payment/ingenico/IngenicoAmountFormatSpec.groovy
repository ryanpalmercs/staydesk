package com.staydesk.payment.ingenico

import spock.lang.Specification
import spock.lang.Unroll

class IngenicoAmountFormatSpec extends Specification {

    @Unroll
    def "toCents(#input) == #expected"() {
        expect:
        IngenicoAmountFormat.toCents(input) == expected

        where:
        input                       | expected
        new BigDecimal("64.17")     | 6417
        new BigDecimal("0.00")      | 0
        new BigDecimal("0.005")     | 1
        new BigDecimal("0.995")     | 100
        new BigDecimal("100")       | 10000
        new BigDecimal("12.994")    | 1299
        new BigDecimal("12.995")    | 1300
    }

    def "rejects negative amounts"() {
        when:
        IngenicoAmountFormat.toCents(new BigDecimal("-1.00"))

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects null"() {
        when:
        IngenicoAmountFormat.toCents(null)

        then:
        thrown(IllegalArgumentException)
    }
}
