package com.staydesk.repository;

import com.staydesk.model.Guest;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface GuestRepository extends ListCrudRepository<Guest, Integer> {

    List<Guest> getGuestByEmail(String email);
}
