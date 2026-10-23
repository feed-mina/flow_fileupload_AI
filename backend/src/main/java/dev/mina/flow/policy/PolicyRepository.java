package dev.mina.flow.policy;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyRepository {
    private final JdbcTemplate jdbc;

    public PolicyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long lockVersion() {
        return jdbc.queryForObject("SELECT version FROM policy_scope WHERE id = 1 FOR UPDATE", Long.class);
    }

    public PolicyView snapshot() {
        long version = jdbc.queryForObject("SELECT version FROM policy_scope WHERE id = 1", Long.class);
        List<PolicyView.Extension> fixed = list("FIXED");
        List<PolicyView.Extension> custom = list("CUSTOM");
        return new PolicyView(version, fixed, custom, custom.size(), 200, 20);
    }

    private List<PolicyView.Extension> list(String kind) {
        return jdbc.query("SELECT id, extension, enabled FROM extension_policy WHERE policy_id = 1 AND kind = ? ORDER BY sort_order, id",
                (rs, row) -> new PolicyView.Extension(rs.getLong("id"), rs.getString("extension"), rs.getBoolean("enabled")), kind);
    }

    public void setFixed(String extension, boolean enabled) {
        jdbc.update("UPDATE extension_policy SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE policy_id = 1 AND kind = 'FIXED' AND extension = ?",
                enabled, extension);
    }

    public boolean exists(String extension) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM extension_policy WHERE policy_id = 1 AND extension = ?)", Boolean.class, extension));
    }

    public int customCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM extension_policy WHERE policy_id = 1 AND kind = 'CUSTOM'", Integer.class);
    }

    public void addCustom(String extension) {
        jdbc.update("INSERT INTO extension_policy (policy_id, extension, kind, enabled) VALUES (1, ?, 'CUSTOM', TRUE)", extension);
    }

    public void advanceVersion() {
        jdbc.update("UPDATE policy_scope SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = 1");
    }

    public int deleteCustom(long id) {
        return jdbc.update("DELETE FROM extension_policy WHERE policy_id = 1 AND kind = 'CUSTOM' AND id = ?", id);
    }

    public String customExtension(long id) {
        return jdbc.query("SELECT extension FROM extension_policy WHERE policy_id = 1 AND kind = 'CUSTOM' AND id = ?",
                (rs, row) -> rs.getString("extension"), id).stream().findFirst().orElse(null);
    }

    public void audit(String action, String extension, boolean enabled) {
        jdbc.update("INSERT INTO policy_change (policy_id, policy_version, action, extension, enabled) SELECT id, version, ?, ?, ? FROM policy_scope WHERE id = 1",
                action, extension, enabled);
    }
}
