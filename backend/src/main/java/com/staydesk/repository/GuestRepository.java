package com.staydesk.repository;

import com.staydesk.model.Guest;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface GuestRepository extends ListCrudRepository<Guest, Integer> {

    Optional<Guest> getGuestByEmail(String email);
}
