ALTER TABLE pix_charges
    ALTER COLUMN created_by TYPE uuid USING created_by::uuid,
    ALTER COLUMN updated_by TYPE uuid USING updated_by::uuid;

ALTER TABLE pix_keys
    ALTER COLUMN created_by TYPE uuid USING created_by::uuid,
    ALTER COLUMN updated_by TYPE uuid USING updated_by::uuid;
