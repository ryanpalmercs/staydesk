package com.staydesk.service;

import com.staydesk.exception.HousekeepingStatusAlreadySetException;
import com.staydesk.exception.InvalidHousekeepingAssignmentException;
import com.staydesk.exception.InvalidHousekeepingTransitionException;
import com.staydesk.exception.RoomNotFoundException;
import com.staydesk.model.Employee;
import com.staydesk.model.HousekeepingTask;
import com.staydesk.model.Room;
import com.staydesk.model.dto.HousekeepingBoardRowDto;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.HousekeepingTaskRepository;
import com.staydesk.repository.RoomRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HousekeepingService {

    private final HousekeepingTaskRepository housekeepingTaskRepository;
    private final RoomRepository roomRepository;
    private final EmployeeRepository employeeRepository;

    public HousekeepingService(HousekeepingTaskRepository housekeepingTaskRepository, RoomRepository roomRepository,
                               EmployeeRepository employeeRepository) {
        this.housekeepingTaskRepository = housekeepingTaskRepository;
        this.roomRepository = roomRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<HousekeepingBoardRowDto> getBoard() {
        Map<Integer, HousekeepingTask> currentByRoomId = housekeepingTaskRepository.findAllCurrent().stream()
                .collect(Collectors.toMap(HousekeepingTask::roomId, t -> t));

        Map<UUID, String> employeeNames = employeeRepository.findActiveHousekeepingStaff().stream()
                .collect(Collectors.toMap(Employee::id, Employee::name));

        return roomRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Room::roomNumber))
                .map(room -> {
                    HousekeepingTask task = currentByRoomId.get(room.id());

                    if (task == null) {
                        return new HousekeepingBoardRowDto(room.id(), room.roomNumber(), HousekeepingTask.Status.CLEAN,
                                null, null, null, null, null);
                    }

                    return new HousekeepingBoardRowDto(room.id(), room.roomNumber(), task.status(),
                            task.assignedEmployeeId(),
                            task.assignedEmployeeId() != null ? employeeNames.get(task.assignedEmployeeId()) : null,
                            task.markedDirtyAt(), task.cleanedAt(), task.inspectedAt());
                })
                .toList();
    }

    public List<Employee> getHousekeepingStaff() {
        return employeeRepository.findActiveHousekeepingStaff().stream()
                .sorted(Comparator.comparing(Employee::name))
                .toList();
    }

    // Always inserts a fresh row, independent of any current row's state - checkout always starts
    // a new turnover cycle regardless of what happened to this room last time.
    public void markDirty(int roomId) {
        LocalDateTime now = LocalDateTime.now();
        housekeepingTaskRepository.save(new HousekeepingTask(0, roomId, null, now, null, null, now, now));
    }

    public void markClean(int roomId) {
        HousekeepingTask task = getOrCreateCurrentTask(roomId);

        if (task.status() == HousekeepingTask.Status.CLEAN) {
            throw new HousekeepingStatusAlreadySetException();
        } else if (task.status() != HousekeepingTask.Status.DIRTY) {
            throw new InvalidHousekeepingTransitionException();
        }

        housekeepingTaskRepository.markClean(task.id(), LocalDateTime.now());
    }

    public void markInspected(int roomId) {
        HousekeepingTask task = getOrCreateCurrentTask(roomId);

        if (task.status() == HousekeepingTask.Status.INSPECTED) {
            throw new HousekeepingStatusAlreadySetException();
        } else if (task.status() != HousekeepingTask.Status.CLEAN) {
            throw new InvalidHousekeepingTransitionException();
        }

        housekeepingTaskRepository.markInspected(task.id(), LocalDateTime.now());
    }

    public void assign(int roomId, @Nullable UUID employeeId) {
        if (employeeId != null) {
            boolean isHousekeepingStaff = employeeRepository.findActiveHousekeepingStaff().stream()
                    .anyMatch(e -> e.id().equals(employeeId));

            if (!isHousekeepingStaff) {
                throw new InvalidHousekeepingAssignmentException();
            }
        }

        HousekeepingTask task = getOrCreateCurrentTask(roomId);
        housekeepingTaskRepository.updateAssignedEmployee(task.id(), employeeId);
    }

    private HousekeepingTask getOrCreateCurrentTask(int roomId) {
        roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);

        return housekeepingTaskRepository.findCurrentByRoomId(roomId)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    return housekeepingTaskRepository.save(new HousekeepingTask(0, roomId, null, null, null, null, now, now));
                });
    }
}
