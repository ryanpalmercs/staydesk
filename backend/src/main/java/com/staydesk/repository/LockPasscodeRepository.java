package com.staydesk.repository;

import com.staydesk.model.LockPasscode;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LockPasscodeRepository extends CrudRepository<LockPasscode, Integer> {
    List<LockPasscode> findByReservationIdAndStatus(int reservationId, LockPasscode.Status status);

    List<LockPasscode> findByStatus(LockPasscode.Status status);

    List<LockPasscode> findByStatusAndAcknowledgedFalse(LockPasscode.Status status);

    @Modifying
    @Query("UPDATE lock_passcodes SET acknowledged = true WHERE id = :id")
    void acknowledge(@Param("id") int id);
}
