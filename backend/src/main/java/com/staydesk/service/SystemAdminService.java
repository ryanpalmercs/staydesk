package com.staydesk.service;

import com.staydesk.exception.AccountAlreadyExistsException;
import com.staydesk.model.Account;
import com.staydesk.model.request.CreateSystemAdminRequest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SystemAdminService {

    private final SupabaseAdminClient supabaseAdminClient;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public SystemAdminService(SupabaseAdminClient supabaseAdminClient, JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.supabaseAdminClient = supabaseAdminClient;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    public Account createSystemAdmin(CreateSystemAdminRequest request) {
        UUID supabaseId;

        try {
            supabaseId = supabaseAdminClient.createUser(request.email(), request.password(), "ADMIN");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new AccountAlreadyExistsException();
            }

            throw e;
        }

        LocalDateTime now = LocalDateTime.now();

        return jdbcAggregateTemplate.insert(new Account(supabaseId, Account.AccountKind.SYSTEM_ADMIN,
                request.displayName(), true, now, now));
    }
}
