package com.staydesk.service;

import com.staydesk.exception.GuestNotFoundException;
import com.staydesk.model.Guest;
import com.staydesk.repository.GuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GuestService {

    private final GuestRepository guestRepository;

    public GuestService(GuestRepository guestRepository) {
        this.guestRepository = guestRepository;
    }

    @Transactional
    public Guest flagGuest(int id, String reason, UUID flaggedBy) {
        guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);

        guestRepository.flagGuest(id, reason, flaggedBy);

        return guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);
    }

    @Transactional
    public Guest unflagGuest(int id) {
        guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);

        guestRepository.unflagGuest(id);

        return guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);
    }

    @Transactional
    public Guest setLegalHold(int id) {
        guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);
        guestRepository.setLegalHold(id);
        return guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);
    }

    @Transactional
    public Guest clearLegalHold(int id) {
        guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);
        guestRepository.clearLegalHold(id);
        return guestRepository.findById(id).orElseThrow(GuestNotFoundException::new);
    }
}