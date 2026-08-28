package com.staydesk.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.model.ContactInfo;
import com.staydesk.model.EncryptedString;
import com.staydesk.model.Employee;
import com.staydesk.model.Guest;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.GuestRepository;
import com.staydesk.security.PiiCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One-time backfill for issue #70: encrypts guest/employee PII columns that predate the
 * EncryptedString converters. Reads raw plaintext directly via JdbcTemplate (the normal
 * repositories now assume ciphertext and would fail to decrypt these rows), then re-saves
 * through the repositories so the Writer converters encrypt on the way back in.
 * Run once with pii.backfill.enabled=true, confirm the app reads guests/employees normally
 * afterward, then unset the flag (or delete this class).
 */
@Component
@ConditionalOnProperty(name = "pii.backfill.enabled", havingValue = "true")
public class PiiBackfillRunner implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PiiBackfillRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final GuestRepository guestRepository;
    private final EmployeeRepository employeeRepository;
    private final PiiCipher piiCipher;
    private final ObjectMapper objectMapper;

    public PiiBackfillRunner(JdbcTemplate jdbcTemplate, GuestRepository guestRepository,
                             EmployeeRepository employeeRepository, PiiCipher piiCipher, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.guestRepository = guestRepository;
        this.employeeRepository = employeeRepository;
        this.piiCipher = piiCipher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        backfillGuests();
        backfillEmployees();
    }

    private void backfillGuests() {
        List<Guest> plaintextGuests = jdbcTemplate.query("""
                SELECT id, first_name, last_name, email, phone_number, flagged, flag_reason, flagged_date,
                       flagged_by, legal_hold, created_at, updated_at
                FROM guests
                """, this::mapPlaintextGuest);

        LOGGER.info("Backfilling {} guest rows", plaintextGuests.size());

        for (Guest guest : plaintextGuests) {
            guestRepository.save(guest);
        }

        LOGGER.info("Guest PII backfill complete");
    }

    private Guest mapPlaintextGuest(ResultSet rs, int rowNum) throws SQLException {
        String email = rs.getString("email");
        UUID flaggedBy = rs.getObject("flagged_by", UUID.class);

        return new Guest(
                rs.getInt("id"),
                new EncryptedString(rs.getString("first_name")),
                new EncryptedString(rs.getString("last_name")),
                new EncryptedString(email),
                piiCipher.hash(email.strip().toLowerCase()),
                new EncryptedString(rs.getString("phone_number")),
                rs.getBoolean("sms_consent"),
                rs.getBoolean("flagged"),
                rs.getString("flag_reason"),
                toLocalDateTime(rs.getTimestamp("flagged_date")),
                flaggedBy,
                rs.getBoolean("legal_hold"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private void backfillEmployees() {
        List<Employee> plaintextEmployees = jdbcTemplate.query("""
                SELECT id, first_name, last_name, email, username, employee_type_id, pay_rate, hire_date, active,
                       contact_info, pay_rate_type, door_access_enabled, created_at, updated_at
                FROM employees
                """, this::mapPlaintextEmployee);

        LOGGER.info("Backfilling {} employee rows", plaintextEmployees.size());

        for (Employee employee : plaintextEmployees) {
            employeeRepository.save(employee);
        }

        LOGGER.info("Employee PII backfill complete");
    }

    private Employee mapPlaintextEmployee(ResultSet rs, int rowNum) throws SQLException {
        String email = rs.getString("email");
        String rawContactInfo = rs.getString("contact_info");
        ContactInfo contactInfo;
        try {
            contactInfo = rawContactInfo == null ? null : objectMapper.readValue(rawContactInfo, ContactInfo.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse existing contact_info during backfill", e);
        }

        return new Employee(
                rs.getObject("id", UUID.class),
                new EncryptedString(rs.getString("first_name")),
                new EncryptedString(rs.getString("last_name")),
                new EncryptedString(email),
                piiCipher.hash(email.strip().toLowerCase()),
                rs.getString("username"),
                rs.getInt("employee_type_id"),
                rs.getBigDecimal("pay_rate"),
                rs.getObject("hire_date", LocalDate.class),
                rs.getBoolean("active"),
                contactInfo,
                Employee.PayRateType.valueOf(rs.getString("pay_rate_type")),
                rs.getBoolean("door_access_enabled"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                rs.getString("quickbooks_employee_id")
        );
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
