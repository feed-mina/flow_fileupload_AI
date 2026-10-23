-- Provision flow_app before running Flyway (see deploy/db-init/10-app-role.sh).
-- Migrations run with the separate administrator connection; app cannot create roles/tables.
DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flow_app') THEN
  GRANT USAGE ON SCHEMA public TO flow_app;
  GRANT SELECT ON policy_scope, extension_policy, upload_record TO flow_app;
  GRANT UPDATE (version, updated_at) ON policy_scope TO flow_app;
  GRANT UPDATE (enabled, updated_at) ON extension_policy TO flow_app;
  GRANT INSERT, DELETE ON extension_policy TO flow_app;
  GRANT INSERT ON policy_change, upload_record TO flow_app;
  GRANT UPDATE (state) ON upload_record TO flow_app;
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO flow_app;
  REVOKE CREATE ON SCHEMA public FROM flow_app;
 END IF;
END $$;
