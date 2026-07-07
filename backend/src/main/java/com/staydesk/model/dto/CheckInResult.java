package com.staydesk.model.dto;

import com.staydesk.model.Reservation;
import com.staydesk.service.LockPasscodeService.PasscodeResult;

public record CheckInResult(Reservation reservation, PasscodeResult.Outcome doorAccessStatus) {
}