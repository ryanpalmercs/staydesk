package com.staydesk.repository;

import com.staydesk.model.reporting.GuestCountRow;
import com.staydesk.model.reporting.RoomReportRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class ReportRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportRepository.class);

    private static final String STATUS_CANCELLED_AND_DATE_BETWEEN_CLAUSE = """
                  r.check_in_date >= ? AND r.check_in_date < ?
                  AND r.status != 'CANCELLED'
            """;

    private final String GET_TOTAL_QUERY = """
            SELECT COALESCE(SUM(fi.amount), 0)
            FROM folio_items fi
            JOIN folios f on fi.folio_id = f.id
            JOIN reservations r ON f.reservation_id = r.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal getTotalRevenue(LocalDate startDate, LocalDate endDate) {

        String sql = GET_TOTAL_QUERY +
                     """
                             WHERE fi.type = 'CHARGE'
                             AND""" +
                     STATUS_CANCELLED_AND_DATE_BETWEEN_CLAUSE;

        LOGGER.debug("SQL: {}", sql);

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, startDate, endDate);
    }

    public BigDecimal getTotalTax(LocalDate startDate, LocalDate endDate) {
        String sql = GET_TOTAL_QUERY +
                     """
                             WHERE fi.type = 'TAX'
                             AND
                             """ +
                     STATUS_CANCELLED_AND_DATE_BETWEEN_CLAUSE;

        LOGGER.debug("SQL: {}", sql);

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, startDate, endDate);
    }

    public Integer getOccupiedNightCount(LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT COALESCE(SUM(LEAST(check_out_date, ?) - GREATEST(check_in_date, ?)), 0)
                FROM reservations
                WHERE status != 'CANCELLED'
                                   AND check_in_date < ? AND check_out_date > ?
                """;

        LOGGER.debug("SQL: {}", sql);

        return jdbcTemplate.queryForObject(sql, Integer.class, endDate, startDate, endDate, startDate);
    }

    public Integer getTotalRoomCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class);
    }

    public List<GuestCountRow> getGuestCountBreakdown(LocalDate startDate, LocalDate endDate) {
        String sql = """
                             SELECT r.guest_count, COUNT(r.id) AS reservation_count, COALESCE(SUM(fi.amount), 0) AS revenue
                             FROM reservations r
                             JOIN folios f ON f.reservation_id = r.id
                             LEFT JOIN folio_items fi ON fi.folio_id = f.id AND fi.type = 'CHARGE'
                             WHERE
                             """ +
                     STATUS_CANCELLED_AND_DATE_BETWEEN_CLAUSE +
                     """
                             
                             GROUP BY r.guest_count
                             ORDER BY r.guest_count
                             """;

        LOGGER.debug("SQL: {}", sql);

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new GuestCountRow(
                        rs.getInt("guest_count"),
                        rs.getInt("reservation_count"),
                        rs.getBigDecimal("revenue")
                ),
                startDate, endDate);
    }

    public List<RoomReportRow> getByRoom(LocalDate startDate, LocalDate endDate) {
        int totalNights = (int) startDate.until(endDate, java.time.temporal.ChronoUnit.DAYS);
        String sql = """
                SELECT ro.id AS room_id,
                       ro.room_number,
                       COALESCE(SUM(
                           LEAST(r.check_out_date, ?) - GREATEST(r.check_in_date, ?)
                       ), 0) AS booked_nights,
                       COALESCE(SUM(fi.amount), 0) AS revenue
                FROM rooms ro
                LEFT JOIN reservations r ON r.room_id = ro.id
                    AND r.status != 'CANCELLED'
                    AND r.check_in_date < ? AND r.check_out_date > ?
                LEFT JOIN folios f ON f.reservation_id = r.id
                LEFT JOIN folio_items fi ON fi.folio_id = f.id AND fi.type = 'CHARGE'
                GROUP BY ro.id, ro.room_number
                ORDER BY booked_nights DESC
                """;

        LOGGER.debug("SQL: {}", sql);

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    int bookedNights = rs.getInt("booked_nights");
                    BigDecimal occupancyRate = totalNights > 0 ?
                                               BigDecimal.valueOf(bookedNights).divide(BigDecimal.valueOf(totalNights), 4, java.math.RoundingMode.HALF_UP) :
                                               BigDecimal.ZERO;
                    return new RoomReportRow(
                            rs.getInt("room_id"),
                            rs.getInt("room_number"),
                            bookedNights,
                            totalNights,
                            occupancyRate,
                            rs.getBigDecimal("revenue")
                    );
                },
                endDate, startDate, endDate, startDate);
    }
}
