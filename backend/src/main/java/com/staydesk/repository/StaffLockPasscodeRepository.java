package com.staydesk.repository;

import com.staydesk.model.StaffLockPasscode;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface StaffLockPasscodeRepository extends CrudRepository<StaffLockPasscode, Integer> {
    List<StaffLockPasscode> findByEmployeeIdAndStatus(UUID employeeId, StaffLockPasscode.Status status);
}