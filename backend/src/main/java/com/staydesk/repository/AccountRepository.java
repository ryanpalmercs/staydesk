package com.staydesk.repository;

import com.staydesk.model.Account;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

public interface AccountRepository extends ListCrudRepository<Account, UUID> {
}
