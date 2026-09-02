package com.staydesk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class RoomTypeAvailabilityRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomTypeAvailabilityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LocalDate> getFullyBookedDates(int roomTypeId, Integer excludeReservationId) {
        String sql = """
                SELECT gs.day::date AS day
                FROM room_types rt
                CROSS JOIN LATERAL generate_series(
                    (SELECT MIN(check_in_date)::timestamp FROM reservations WHERE room_type_id = ? AND status NOT IN ('CANCELLED', 'CHECKED_OUT')
                        AND (?::int IS NULL OR id != ?::int)),
                    (SELECT MAX(check_out_date)::timestamp FROM reservations WHERE room_type_id = ? AND status NOT IN ('CANCELLED', 'CHECKED_OUT')
                        AND (?::int IS NULL OR id != ?::int)) - INTERVAL '1 day',
                    INTERVAL '1 day'
                ) AS gs(day)
                WHERE rt.id = ?
                  AND (
                      SELECT COUNT(*) FROM reservations r
                      WHERE r.room_type_id = ?
                        AND r.status NOT IN ('CANCELLED', 'CHECKED_OUT')
                        AND r.check_in_date <= gs.day::date
                        AND r.check_out_date > gs.day::date
                        AND (?::int IS NULL OR r.id != ?::int)
                  ) >= rt.available_count
                ORDER BY gs.day
                """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getDate("day").toLocalDate(),
                roomTypeId, excludeReservationId, excludeReservationId,
                roomTypeId, excludeReservationId, excludeReservationId,
                roomTypeId, roomTypeId, excludeReservationId, excludeReservationId);
    }
}
