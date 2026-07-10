package com.staydesk.controller;

import com.staydesk.model.RoomType;
import com.staydesk.repository.RoomTypeAvailabilityRepository;
import com.staydesk.repository.RoomTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/room-types")
public class RoomTypeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTypeController.class);

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeAvailabilityRepository roomTypeAvailabilityRepository;

    public RoomTypeController(RoomTypeRepository roomTypeRepository, RoomTypeAvailabilityRepository roomTypeAvailabilityRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomTypeAvailabilityRepository = roomTypeAvailabilityRepository;
    }

    @GetMapping
    public List<RoomType> getRoomTypes() {
        LOGGER.info("Getting all room types");
        return roomTypeRepository.findAll();
    }

    @GetMapping("{id}/occupied-dates")
    public List<LocalDate> getFullyBookedDates(@PathVariable int id) {
        return roomTypeAvailabilityRepository.getFullyBookedDates(id);
    }
}
