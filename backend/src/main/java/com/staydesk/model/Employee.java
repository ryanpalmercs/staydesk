package com.staydesk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("employees")
public record Employee(@Id UUID id, EncryptedString firstName, EncryptedString lastName, EncryptedString email,
                       @JsonIgnore String emailHash, String username, int employeeTypeId,
                       BigDecimal payRate, LocalDate hireDate, boolean active, ContactInfo contactInfo, PayRateType payRateType,
                       boolean doorAccessEnabled, LocalDateTime createdAt, LocalDateTime updatedAt, String quickbooksEmployeeId) {

    @JsonProperty()
    public String name() {
        return firstName.value() + " " + lastName.value();
    }

    @JsonProperty()
    public String payRateTypeDisplayName() {
        return payRateType != null ? payRateType.getDisplayName() : null;
    }

    public enum PayRateType {
        HOURLY ("hr"), DAILY ("day"), SALARY ("yr");

        private final String displayName;

        PayRateType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
