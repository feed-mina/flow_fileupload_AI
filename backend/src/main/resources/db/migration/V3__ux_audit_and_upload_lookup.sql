CREATE TABLE policy_change (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy_scope(id),
    policy_version BIGINT NOT NULL CHECK (policy_version > 0),
    action VARCHAR(16) NOT NULL CHECK (action IN ('FIXED_SET', 'CUSTOM_ADDED', 'CUSTOM_DELETED')),
    extension VARCHAR(20) NOT NULL CHECK (extension ~ '^[a-z0-9]{1,20}$'),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (policy_id, policy_version)
);
-- The leading policy_id column of the unique index also indexes the FK.
-- Existing uploads get a lookup key without changing their file ID or bytes.
ALTER TABLE upload_record ADD COLUMN request_id UUID;
UPDATE upload_record SET request_id = id;
ALTER TABLE upload_record ALTER COLUMN request_id SET NOT NULL;
ALTER TABLE upload_record ADD CONSTRAINT upload_record_request_id_key UNIQUE (request_id);
