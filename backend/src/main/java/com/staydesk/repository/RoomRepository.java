package com.staydesk.repository;

import com.staydesk.model.Room;
import org.springframework.data.repository.ListCrudRepository;

public interface RoomRepository extends ListCrudRepository<Room, Integer> {
}
