INSERT INTO rates (rate_type, guest_count, amount)
VALUES ('NIGHTLY', 1, 84.17),
       ('NIGHTLY', 2, 95.00),
       ('NIGHTLY', 3, 105.83),
       ('NIGHTLY', 4, 116.66),
       ('WEEKLY_5', 1, 270.50),
       ('WEEKLY_7', 1, 362.70),
       ('WEEKLY_5', 2, 286.80),
       ('WEEKLY_7', 2, 385.00),
       ('WEEKLY_5', 3, 339.00),
       ('WEEKLY_7', 3, 458.60),
       ('WEEKLY_5', 4, 359.00),
       ('WEEKLY_7', 4, 478.60)
ON CONFLICT (rate_type, guest_count)
    DO UPDATE SET amount     = EXCLUDED.amount,
                  updated_at = NOW();
