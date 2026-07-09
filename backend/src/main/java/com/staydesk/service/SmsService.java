package com.staydesk.service;

import com.staydesk.model.Guest;
import com.staydesk.model.Reservation;
import com.twilio.Twilio;
import com.twilio.exception.TwilioException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SmsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmsService.class);

    private final PropertySettingsService propertySettingsService;

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    public SmsService(PropertySettingsService propertySettingsService) {
        this.propertySettingsService = propertySettingsService;
    }

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    private void sendSms(String to, String body) {
        try {
            Message.creator(new PhoneNumber(to), new PhoneNumber(fromNumber), body).create();
        } catch (TwilioException e) {
            LOGGER.error("Failed to send SMS to {}.", to, e);
        }
    }

    private String interpolate(String template, Map<String, String> variables) {
        String result = template;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return result;
    }

    public void sendConfirmation(Guest guest, Reservation reservation) {
        String template = propertySettingsService.getProperty("sms_confirmation_template").value();

        Map<String, String> variables = Map.of(
                "guestFirstName", guest.firstName().value(),
                "guestLastName", guest.lastName().value(),
                "checkInDate", reservation.checkInDate().toString(),
                "checkOutDate", reservation.checkOutDate().toString(),
                "confirmationNumber", String.valueOf(reservation.id())
        );

        sendSms(guest.phoneNumber().value(), interpolate(template, variables));
    }

    public void sendCheckInLink(Guest guest, Reservation reservation, String link) {
        // NO OP
        LOGGER.info("Check-in link SMS stub - reservation {}, link {}", reservation.id(), link);
    }

    public void sendCheckInComplete(Guest guest, Reservation reservation, int roomNumber, String doorCode) {
        String template = propertySettingsService.getProperty("sms_checkin_complete_template").value();

        Map<String, String> variables = Map.of(
                "guestFirstName", guest.firstName().value(),
                "roomNumber", String.valueOf(roomNumber),
                "doorCode", doorCode
        );

        sendSms(guest.phoneNumber().value(), interpolate(template, variables));
    }

    public void sendDoorCodeResolved(String phoneNumber, int roomNumber) {
        String template = propertySettingsService.getProperty("sms_door_code_resolved_template").value();
        Map<String, String> variables = Map.of("roomNumber", String.valueOf(roomNumber));

        sendSms(phoneNumber, interpolate(template, variables));
    }
}
