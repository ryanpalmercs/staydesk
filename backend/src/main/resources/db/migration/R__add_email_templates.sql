INSERT INTO property_settings (name, value)
VALUES ('email_confirmation_subject', 'Your reservation at Martin House Motel is confirmed'),
       ('email_confirmation_body',
        '<p>Hi {{guestFirstName}},</p>' ||
        '<p>Your reservation at Martin House Motel is confirmed for <strong>{{checkInDate}}</strong> through ' ||
        '<strong>{{checkOutDate}}</strong>.</p>' ||
        '<p>Confirmation number: <strong>{{confirmationNumber}}</strong></p>' ||
        '<p>731 South Main Street, Brookfield, Missouri 64628<br>(660) 258-7257</p>'),
       ('email_checkin_link_subject', 'Complete your remote check-in for Martin House Motel'),
       ('email_checkin_link_body',
        '<p>Hi {{guestFirstName}},</p>' ||
        '<p>You can complete check-in for your upcoming stay before you arrive. ' ||
        '<a href="{{link}}">Complete remote check-in</a></p>' ||
        '<p>If you would rather check in at the front desk, no action is needed.</p>'),
       ('email_checkin_complete_subject', 'Your room is ready — Martin House Motel'),
       ('email_checkin_complete_body',
        '<p>Hi {{guestFirstName}},</p>' ||
        '<p>Your room {{roomNumber}} is ready. Your door code is <strong>{{doorCode}}</strong>.</p>' ||
        '<p>See you soon!</p>')
ON CONFLICT (name) DO NOTHING;
