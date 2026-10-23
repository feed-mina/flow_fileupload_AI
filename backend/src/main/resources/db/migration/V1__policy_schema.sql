CREATE TABLE policy_scope (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE extension_policy (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policy_scope(id),
    extension VARCHAR(20) NOT NULL CHECK (extension ~ '^[a-z0-9]{1,20}$'),
    kind VARCHAR(6) NOT NULL CHECK (kind IN ('FIXED', 'CUSTOM')),
    enabled BOOLEAN NOT NULL,
    sort_order SMALLINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_policy_extension UNIQUE (policy_id, extension),
    CONSTRAINT ck_kind_extension CHECK (
        (kind = 'FIXED' AND extension IN ('bat', 'cmd', 'com', 'cpl', 'exe', 'scr', 'js'))
        OR (kind = 'CUSTOM' AND extension NOT IN ('bat', 'cmd', 'com', 'cpl', 'exe', 'scr', 'js') AND enabled)
    )
);

INSERT INTO policy_scope (id) VALUES (1);
INSERT INTO extension_policy (policy_id, extension, kind, enabled, sort_order) VALUES
    (1, 'bat', 'FIXED', FALSE, 1),
    (1, 'cmd', 'FIXED', FALSE, 2),
    (1, 'com', 'FIXED', FALSE, 3),
    (1, 'cpl', 'FIXED', FALSE, 4),
    (1, 'exe', 'FIXED', FALSE, 5),
    (1, 'scr', 'FIXED', FALSE, 6),
    (1, 'js',  'FIXED', FALSE, 7);
