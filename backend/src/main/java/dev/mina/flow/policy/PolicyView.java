package dev.mina.flow.policy;

import java.util.List;

public record PolicyView(long version, List<Extension> fixed, List<Extension> custom,
                         int customCount, int maxCount, int maxLength) {
    public record Extension(long id, String extension, boolean enabled) {}
}
