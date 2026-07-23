UPDATE property_settings SET value = 'authorizenet' WHERE name = 'payment_provider';

DROP TABLE stripe_connections;