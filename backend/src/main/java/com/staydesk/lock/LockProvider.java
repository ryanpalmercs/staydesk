package com.staydesk.lock;

import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

public interface LockProvider {
    /**
     * Issues an already-chosen passcode/PIN to the given room's lock. Unlike a vendor-generated
     * code, {@code code} is decided by the caller so it can be reused verbatim on retry and so
     * {@code label} can be parsed back out of vendor access logs to attribute door-access events.
     */
    CodeResult issueCode(String roomIdentifier, String code, String label, LocalDateTime validFrom,
                        @Nullable LocalDateTime validUntil, @Nullable String guestPhone);

    void revokeCode(String roomIdentifier, String codeId);

    LockStatus getStatus(String roomIdentifier);
}
