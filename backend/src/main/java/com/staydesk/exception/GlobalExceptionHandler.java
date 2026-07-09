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
}
