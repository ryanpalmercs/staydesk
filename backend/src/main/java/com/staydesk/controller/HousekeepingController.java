package com.staydesk.controller;

import com.staydesk.exception.InvalidHousekeepingTransitionException;
import com.staydesk.model.Employee;
import com.staydesk.model.dto.HousekeepingBoardRowDto;
import com.staydesk.model.request.AssignHousekeepingRequest;
import com.staydesk.model.request.UpdateHousekeepingStatusRequest;
import com.staydesk.service.HousekeepingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/housekeeping")
public class HousekeepingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(HousekeepingController.class);

    private final HousekeepingService housekeepingService;

    public HousekeepingController(HousekeepingService housekeepingService) {
        this.housekeepingService = housekeepingService;
    }

    @GetMapping("board")
    public List<HousekeepingBoardRowDto> getBoard() {
        return housekeepingService.getBoard();
    }

    @GetMapping("staff")
    public List<Employee> getStaff() {
        return housekeepingService.getHousekeepingStaff();
    }

    @PutMapping("rooms/{roomId}/status")
    public void updateStatus(@PathVariable int roomId, @RequestBody UpdateHousekeepingStatusRequest request) {
        LOGGER.info("Updating housekeeping status for room {} to {}", roomId, request.status());

        switch (request.status()) {
            case CLEAN -> housekeepingService.markClean(roomId);
            case INSPECTED -> housekeepingService.markInspected(roomId);
            case DIRTY -> throw new InvalidHousekeepingTransitionException();
        }
    }

    @PutMapping("rooms/{roomId}/assign")
    public void assign(@PathVariable int roomId, @RequestBody AssignHousekeepingRequest request) {
        LOGGER.info("Assigning room {} to employee {}", roomId, request.employeeId());

        housekeepingService.assign(roomId, request.employeeId());
    }
}
