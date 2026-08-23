package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.infection.StrainData;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class StrainFingerprint {
    private StrainFingerprint() {}

    public static String ofPayload(String payload) {
        String canonical = StrainData.canonicalGeneticPayload(payload == null ? "" : payload);
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
