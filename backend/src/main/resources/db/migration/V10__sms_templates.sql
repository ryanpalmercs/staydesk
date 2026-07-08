INSERT INTO property_settings (name, value)
VALUES ('sms_confirmation_template',
        'Hi {{guestFirstName}}, your reservation at Martin House is confirmed for {{checkInDate}} ' ||
        'through {{checkOutDate}}, Room {{roomNumber}}. Confirmation #{{confirmationNumber}}.'),
       ('sms_checkin_link_template', 'Hi {{guestFirstName}}, your remote check-in link is ready: {{link}}'),
       ('sms_checkin_complete_template',
        'Hi {{guestFirstName}}, your room {{roomNumber}} is ready!

Your door code is {{doorCode}}');