package com.staydesk.repository;

import com.staydesk.model.PosDevice;
import org.springframework.data.repository.ListCrudRepository;

public interface PosDeviceRepository extends ListCrudRepository<PosDevice, Integer> {
}