package net.jenkimods.bioforge.infection.naming;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class StrainNameClientCache {
    private static final AtomicReference<Map<String, String>> NAMES =
            new AtomicReference<>(Map.of());

    private StrainNameClientCache() {}

    public static void replace(Map<String, String> names) {
        NAMES.set(Map.copyOf(new LinkedHashMap<>(names)));
    }

    public static Optional<String> find(String fingerprint) {
        return Optional.ofNullable(NAMES.get().get(fingerprint));
    }

    public static Map<String, String> all() {
        return NAMES.get();
    }
}
