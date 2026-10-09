package com.staydesk.service;

import com.staydesk.model.Guest;
import com.staydesk.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);
    private static final String SENDGRID_URL = "https://api.sendgrid.com/v3/mail/send";

    private final RestClient restClient = RestClient.create();
    private final PropertySettingsService propertySettingsService;

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${sendgrid.from-name}")
    private String fromName;

    public EmailService(PropertySettingsService propertySettingsService) {
        this.propertySettingsService = propertySettingsService;
    }

    private String interpolate(String template, Map<String, String> variables) {
        String result = template;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return result;
    }

    private void sendEmail(String toEmail, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("SendGrid API key not configured; skipping email to {}", toEmail);
            return;
        }

        SendGridRequest body = new SendGridRequest(
                List.of(new Personalization(List.of(new EmailAddress(toEmail)))),
                new EmailAddress(fromEmail, fromName),
                subject,
                List.of(new Content("text/html", htmlBody)));

        try {
            restClient.post()
                      .uri(SENDGRID_URL)
                      .header("Authorization", "Bearer " + apiKey)
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(body)
                      .retrieve()
                      .toBodilessEntity();
        } catch (RestClientException e) {
            LOGGER.error("Failed to send email to {}.", toEmail, e);
        }
    }

    public void sendConfirmation(Guest guest, Reservation reservation) {
        if (guest.email() == null) {
            return;
        }

        String subject = propertySettingsService.getProperty("email_confirmation_subject").value();
        String template = propertySettingsService.getProperty("email_confirmation_body").value();

        Map<String, String> variables = Map.of(
                "guestFirstName", guest.firstName().value(),
                "guestLastName", guest.lastName().value(),
                "checkInDate", reservation.checkInDate().toString(),
                "checkOutDate", reservation.checkOutDate().toString(),
                "confirmationNumber", reservation.confirmationCode() != null ? reservation.confirmationCode() : "NO CONFIRMATION NUMBER"
        );

        sendEmail(guest.email().value(), subject, interpolate(template, variables));
    }

    public void sendCheckInLink(Guest guest, Reservation reservation, String link) {
        if (guest.email() == null) {
            return;
        }

        String subject = propertySettingsService.getProperty("email_checkin_link_subject").value();
        String template = propertySettingsService.getProperty("email_checkin_link_body").value();

        Map<String, String> variables = Map.of(
                "guestFirstName", guest.firstName().value(),
                "link", link
        );

        sendEmail(guest.email().value(), subject, interpolate(template, variables));
    }

    public void sendCheckInComplete(Guest guest, int roomNumber, String doorCode) {
        if (guest.email() == null) {
            return;
        }

        String subject = propertySettingsService.getProperty("email_checkin_complete_subject").value();
        String template = propertySettingsService.getProperty("email_checkin_complete_body").value();

        Map<String, String> variables = Map.of(
                "guestFirstName", guest.firstName().value(),
                "roomNumber", String.valueOf(roomNumber),
                "doorCode", doorCode
        );

        sendEmail(guest.email().value(), subject, interpolate(template, variables));
    }

    private record SendGridRequest(List<Personalization> personalizations, EmailAddress from, String subject,
                                   List<Content> content) {
    }

    private record Personalization(List<EmailAddress> to) {
    }

    private record EmailAddress(String email, String name) {
        private EmailAddress(String email) {
            this(email, null);
        }
    }

    private record Content(String type, String value) {
    }
}
