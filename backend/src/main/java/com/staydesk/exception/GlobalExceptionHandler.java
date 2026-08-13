package com.staydesk.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AlreadyClockedInException.class)
    public ResponseEntity<String> handleAlreadyClockedInException(AlreadyClockedInException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(NotClockedInException.class)
    public ResponseEntity<String> handleNotClockedInException(NotClockedInException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(GuestNotFoundException.class)
    public ResponseEntity<String> handleGuestNotFoundException(GuestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(RoomTypeNotFoundException.class)
    public ResponseEntity<String> handleRoomTypeNotFoundException(RoomTypeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(RoomTypeUnavailableException.class)
    public ResponseEntity<String> handleRoomTypeUnavailableException(RoomTypeUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(NoRoomAvailableException.class)
    public ResponseEntity<String> handleNoRoomAvailableException(NoRoomAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(PosDeviceNotFoundException.class)
    public ResponseEntity<String> handlePosDeviceNotFoundException(PosDeviceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<String> handleAccountAlreadyExistsException(AccountAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(NoReusableCredentialException.class)
    public ResponseEntity<String> handleNoReusableCredentialException(NoReusableCredentialException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(FolioNotClosedException.class)
    public ResponseEntity<String> handleFolioNotClosedException(FolioNotClosedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(IncidentChargeRequestNotFoundException.class)
    public ResponseEntity<String> handleIncidentChargeRequestNotFoundException(IncidentChargeRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IncidentChargeAlreadyDecidedException.class)
    public ResponseEntity<String> handleIncidentChargeAlreadyDecidedException(IncidentChargeAlreadyDecidedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(FolioNotFoundException.class)
    public ResponseEntity<String> handleFolioNotFoundException(FolioNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(CardPresentRecordOnlyDisabledException.class)
    public ResponseEntity<String> handleCardPresentRecordOnlyDisabledException(CardPresentRecordOnlyDisabledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(StayAlreadySettledException.class)
    public ResponseEntity<String> handleStayAlreadySettledException(StayAlreadySettledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(RateNotFoundException.class)
    public ResponseEntity<String> handleRateNotFoundException(RateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
