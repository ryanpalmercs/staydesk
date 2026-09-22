package com.staydesk.repository;

import com.staydesk.model.dto.RoomTypeAvailabilityDto;
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

    public List<RoomTypeAvailabilityDto> getAvailabilityGrid(LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT rt.id AS room_type_id, gs.day::date AS day,
                       rt.available_count - (
                           SELECT COUNT(*) FROM reservations r LEFT JOIN guests g ON g.id = r.guest_id
                           WHERE r.room_type_id = rt.id
                             AND r.status NOT IN ('CANCELLED', 'CHECKED_OUT', 'NO_SHOW')
                             AND r.check_in_date <= gs.day::date
                             AND (r.check_out_date > gs.day::date OR (r.status = 'CHECKED_IN' AND COALESCE(g.regular_guest, false)))
                       ) AS available_count
                FROM room_types rt
                CROSS JOIN generate_series(?::date, ?::date - INTERVAL '1 day', INTERVAL '1 day') AS gs(day)
                WHERE EXISTS (SELECT 1 FROM rooms rm WHERE rm.room_type_id = rt.id)
                ORDER BY rt.id, gs.day
                """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new RoomTypeAvailabilityDto(
                        rs.getInt("room_type_id"),
                        rs.getDate("day").toLocalDate(),
                        rs.getInt("available_count")),
                startDate, endDate);
    }

    public List<LocalDate> getFullyBookedDates(int roomTypeId, Integer excludeReservationId) {
        String sql = """
                WITH active_reservations AS (
                    SELECT r.*, COALESCE(g.regular_guest, false) AS is_regular_guest
                    FROM reservations r
                    LEFT JOIN guests g ON g.id = r.guest_id
                    WHERE r.room_type_id = ?
                      AND r.status NOT IN ('CANCELLED', 'CHECKED_OUT')
                      AND (?::int IS NULL OR r.id != ?::int)
                ),
                bounds AS (
                    SELECT
                        MIN(check_in_date) AS start_date,
                        -- A CHECKED_IN Regular Guest occupies their room type indefinitely (see
                        -- ReservationRepository), so the series has to extend well past their actual
                        -- check_out_date for the calendar to grey out the dates that block.
                        GREATEST(
                            MAX(check_out_date),
                            CASE WHEN bool_or(status = 'CHECKED_IN' AND is_regular_guest)
                                 THEN (CURRENT_DATE + INTERVAL '2 years')::date
                                 ELSE MAX(check_out_date)
                            END
                        ) AS end_date
                    FROM active_reservations
                )
                SELECT gs.day::date AS day
                FROM room_types rt
                CROSS JOIN LATERAL generate_series(
                    (SELECT start_date FROM bounds)::timestamp,
                    (SELECT end_date FROM bounds)::timestamp - INTERVAL '1 day',
                    INTERVAL '1 day'
                ) AS gs(day)
                WHERE rt.id = ?
                  AND (
                      SELECT COUNT(*) FROM active_reservations ar
                      WHERE ar.check_in_date <= gs.day::date
                        AND (ar.check_out_date > gs.day::date OR (ar.status = 'CHECKED_IN' AND ar.is_regular_guest))
                  ) >= rt.available_count
                ORDER BY gs.day
                """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getDate("day").toLocalDate(),
                roomTypeId, excludeReservationId, excludeReservationId, roomTypeId);
    }
}
