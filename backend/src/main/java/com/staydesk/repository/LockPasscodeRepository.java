package com.staydesk.repository;

import com.staydesk.model.LockPasscode;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface LockPasscodeRepository extends CrudRepository<LockPasscode, Integer> {
    List<LockPasscode> findByReservationIdAndStatus(int reservationId, LockPasscode.Status status);
}