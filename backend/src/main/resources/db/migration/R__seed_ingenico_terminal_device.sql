INSERT INTO pos_devices (device_id, friendly_name, location)
VALUES ('ingenico-bridge', 'Front Desk', 'Front Desk')
ON CONFLICT (device_id) DO NOTHING;