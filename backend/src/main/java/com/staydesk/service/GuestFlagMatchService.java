package com.staydesk.service;

import com.staydesk.model.Guest;
import com.staydesk.repository.GuestRepository;
import com.staydesk.security.PiiCipher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Matches a guest-submitted online booking against existing flagged guest records, per #99.
 * Matches on name + email OR name + phone, reusing the same blind-index hash lookups the
 * staff-facing guest search already relies on for exact-match email/phone lookups.
 */
@Service
public class GuestFlagMatchService {

    private final GuestRepository guestRepository;
    private final PiiCipher piiCipher;

    public GuestFlagMatchService(GuestRepository guestRepository, PiiCipher piiCipher) {
        this.guestRepository = guestRepository;
        this.piiCipher = piiCipher;
    }

    public boolean isBookingBlocked(String firstName, String lastName, String email, String phoneNumber) {
        String emailHash = piiCipher.hash(email.strip().toLowerCase());
        String phoneHash = piiCipher.hash(phoneNumber);

        return matchesFlagged(guestRepository.findByEmailHash(emailHash).stream().toList(), firstName, lastName)
               || matchesFlagged(guestRepository.findByPhoneHash(phoneHash), firstName, lastName);
    }

    private boolean matchesFlagged(List<Guest> candidates, String firstName, String lastName) {
        return candidates.stream()
                         .filter(Guest::flagged)
                         .anyMatch(guest -> namesMatch(guest, firstName, lastName));
    }

    private boolean namesMatch(Guest guest, String firstName, String lastName) {
        return guest.firstName().value().strip().equalsIgnoreCase(firstName.strip())
               && guest.lastName().value().strip().equalsIgnoreCase(lastName.strip());
    }
}
