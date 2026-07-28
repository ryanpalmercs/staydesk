package com.staydesk.service

import com.staydesk.exception.AccountAlreadyExistsException
import com.staydesk.model.Account
import com.staydesk.model.request.CreateSystemAdminRequest
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Specification

class SystemAdminServiceSpec extends Specification {

    SupabaseAdminClient supabaseAdminClient = Mock()
    JdbcAggregateTemplate jdbcAggregateTemplate = Mock()

    SystemAdminService systemAdminService = new SystemAdminService(supabaseAdminClient, jdbcAggregateTemplate)

    def "creates a system admin, inserts an Account with kind SYSTEM_ADMIN, and returns the saved account"() {
        given:
        def request = new CreateSystemAdminRequest("admin@staydesk.com", "s3cret!", "Jane Admin")
        def supabaseId = UUID.randomUUID()

        // only one insert() call happens in this service, so there's no ordering to assert
        // (unlike EmployeeServiceSpec, which chains then: blocks across two insert calls)
        supabaseAdminClient.createUser("admin@staydesk.com", "s3cret!", "ADMIN") >> supabaseId

        when:
        def result = systemAdminService.createSystemAdmin(request)

        then:
        1 * jdbcAggregateTemplate.insert({
            it instanceof Account && it.id() == supabaseId && it.kind() == Account.AccountKind.SYSTEM_ADMIN &&
                    it.displayName() == "Jane Admin" && it.active() && it.createdAt() == it.updatedAt()
        }) >> { args -> args[0] }

        result.id() == supabaseId
        result.kind() == Account.AccountKind.SYSTEM_ADMIN
        result.displayName() == "Jane Admin"
        result.active()
    }

    def "throws AccountAlreadyExistsException and inserts nothing when Supabase responds 422 Unprocessable Entity"() {
        given:
        def request = new CreateSystemAdminRequest("admin@staydesk.com", "s3cret!", "Jane Admin")
        supabaseAdminClient.createUser(*_) >> { throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY) }

        when:
        systemAdminService.createSystemAdmin(request)

        then:
        thrown(AccountAlreadyExistsException)
        0 * jdbcAggregateTemplate.insert(_)
    }

    def "rethrows the original HttpClientErrorException for non-422 Supabase failures and inserts nothing"() {
        given:
        def request = new CreateSystemAdminRequest("admin@staydesk.com", "s3cret!", "Jane Admin")
        supabaseAdminClient.createUser(*_) >> { throw new HttpClientErrorException(HttpStatus.BAD_REQUEST) }

        when:
        systemAdminService.createSystemAdmin(request)

        then:
        def ex = thrown(HttpClientErrorException)
        ex.statusCode == HttpStatus.BAD_REQUEST
        0 * jdbcAggregateTemplate.insert(_)
    }
}
