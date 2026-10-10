package com.staydesk.migration;

import com.staydesk.model.Guest;
import com.staydesk.repository.GuestRepository;
import com.staydesk.security.PiiCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time backfill for issue #99: populates guests.phone_hash (added in
 * R__add_phone_hash_to_guests) for guest rows that existed before the column was introduced, so
 * flagged-guest matching by phone works for guests created before this migration, not just new
 * ones. Reads phone numbers through the normal repository (which decrypts via the JDBC
 * converter) and writes back only the hash.
 * Run once with pii.backfill.enabled=true, confirm flagged-guest matching works, then unset
 * the flag (or delete this class).
 */
@Component
@ConditionalOnProperty(name = "pii.backfill.enabled", havingValue = "true")
public class PhoneHashBackfillRunner implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhoneHashBackfillRunner.class);

    private final GuestRepository guestRepository;
    private final PiiCipher piiCipher;

    public PhoneHashBackfillRunner(GuestRepository guestRepository, PiiCipher piiCipher) {
        this.guestRepository = guestRepository;
        this.piiCipher = piiCipher;
    }

    @Override
    public void run(String... args) {
        List<Guest> guests = guestRepository.findAll();
        LOGGER.info("Backfilling phone_hash for {} guest rows", guests.size());

        for (Guest guest : guests) {
            guestRepository.updatePhoneHash(guest.id(), piiCipher.hash(guest.phoneNumber().value()));
        }

        LOGGER.info("Guest phone_hash backfill complete");
    }
}
