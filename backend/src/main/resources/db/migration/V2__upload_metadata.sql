CREATE TABLE upload_record (
    id UUID PRIMARY KEY,
    original_name TEXT NOT NULL CHECK (octet_length(original_name) BETWEEN 1 AND 255),
    extension VARCHAR(20) NOT NULL CHECK (extension ~ '^[a-z0-9]{1,20}$'),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    policy_version BIGINT NOT NULL CHECK (policy_version >= 0),
    state VARCHAR(7) NOT NULL CHECK (state IN ('PENDING', 'READY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- policy_version is the immutable snapshot used for this upload, not a FK
-- to the mutable current version. File bytes stay outside PostgreSQL/webroot.
