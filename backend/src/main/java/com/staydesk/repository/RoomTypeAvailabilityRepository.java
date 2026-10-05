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

    /**
     * True if every day in [checkInDate, checkOutDate) has at least one free room of this type.
     * Checks actual per-day concurrent occupancy rather than counting how many reservations
     * overlap the range at all - a handful of short reservations scattered through a long
     * candidate range can each overlap it without ever occupying all rooms on the same day, so a
     * raw overlap count against total capacity produces false "unavailable" results for long
     * stays. Same generate_series approach as getFullyBookedDates below, scoped to the one range
     * being checked instead of scanning out to the furthest-booked date.
     */
    public boolean isAvailableForRange(int roomTypeId, LocalDate checkInDate, LocalDate checkOutDate, Integer excludeReservationId) {
        String sql = """
                SELECT NOT EXISTS (
                    SELECT 1
                    FROM generate_series(?::date, ?::date - INTERVAL '1 day', INTERVAL '1 day') AS gs(day)
                    WHERE (
                        SELECT COUNT(*) FROM reservations r LEFT JOIN guests g ON g.id = r.guest_id
                        WHERE r.room_type_id = ?
                          AND r.status NOT IN ('CANCELLED', 'CHECKED_OUT', 'NO_SHOW')
                          AND (?::int IS NULL OR r.id != ?::int)
                          AND r.check_in_date <= gs.day::date
                          AND (r.check_out_date > gs.day::date OR (r.status = 'CHECKED_IN' AND COALESCE(g.regular_guest, false)))
                    ) >= (SELECT available_count FROM room_types WHERE id = ?)
                )
                """;

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class,
                checkInDate, checkOutDate, roomTypeId, excludeReservationId, excludeReservationId, roomTypeId));
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
