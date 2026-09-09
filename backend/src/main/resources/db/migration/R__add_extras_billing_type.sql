ALTER TABLE extras
    ADD COLUMN IF NOT EXISTS billing_type VARCHAR NOT NULL DEFAULT 'FLAT';

INSERT INTO extras (name, price, billing_type)
SELECT 'Pet Fee', 25.00, 'PER_NIGHT'
WHERE NOT EXISTS (SELECT 1 FROM extras WHERE name = 'Pet Fee');

INSERT INTO extras (name, price, billing_type)
SELECT 'Rollaway Cot', 25.00, 'PER_NIGHT'
WHERE NOT EXISTS (SELECT 1 FROM extras WHERE name = 'Rollaway Cot');
