package com.staydesk.lock;

import com.staydesk.repository.LockStateRepository;
import com.staydesk.service.SifelyLockService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service("sifely")
public class SifelyLockProvider implements LockProvider {

    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");

    private final SifelyLockService sifelyLockService;
    private final LockStateRepository lockStateRepository;

    public SifelyLockProvider(SifelyLockService sifelyLockService, LockStateRepository lockStateRepository) {
        this.sifelyLockService = sifelyLockService;
        this.lockStateRepository = lockStateRepository;
    }

    @Override
    public CodeResult issueCode(String roomIdentifier, String code, String label, LocalDateTime validFrom,
                                LocalDateTime validUntil, String guestPhone) {
        long lockId = Long.parseLong(roomIdentifier);
        long startMillis = validFrom.atZone(PROPERTY_ZONE).toInstant().toEpochMilli();
        long endMillis = validUntil == null ? 0 : validUntil.atZone(PROPERTY_ZONE).toInstant().toEpochMilli();
        int keyboardPwdType = validUntil == null ? 2 : 3;

        try {
            String keyboardPwdId = sifelyLockService.createPasscode(lockId, code, label, startMillis, endMillis, keyboardPwdType);
            return new CodeResult(true, keyboardPwdId, code, null);
        } catch (Exception e) {
            return new CodeResult(false, null, code, e.getMessage());
        }
    }

    @Override
    public void revokeCode(String roomIdentifier, String codeId) {
        sifelyLockService.deletePasscode(Long.parseLong(roomIdentifier), Long.parseLong(codeId));
    }

    @Override
    public LockStatus getStatus(String roomIdentifier) {
        long lockId = Long.parseLong(roomIdentifier);

        return lockStateRepository.findById(lockId)
                                  .map(ls -> new LockStatus(ls.state(), ls.batteryLevel(), ls.lastEventAt()))
                                  .orElse(new LockStatus(LockStatus.State.UNKNOWN, null, null));
    }
}
