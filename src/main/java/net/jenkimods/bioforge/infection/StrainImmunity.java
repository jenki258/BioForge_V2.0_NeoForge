package net.jenkimods.bioforge.infection;

import java.util.Locale;







public record StrainImmunity(String fingerprint, String displayName,
                             int remainingTicks, float strength) {
    public static final int MAX_NAME_LENGTH = 96;

    public StrainImmunity {
        fingerprint = normalizeFingerprint(fingerprint);
        displayName = sanitizeName(displayName, fingerprint);
        remainingTicks = Math.max(0, remainingTicks);
        strength = Math.max(0.0F, Math.min(1.0F,
                Float.isFinite(strength) ? strength : 0.0F));
    }

    public StrainImmunity(String fingerprint, String displayName,
                          int remainingTicks) {
        this(fingerprint, displayName, remainingTicks, 1.0F);
    }

    public boolean isActive() {
        return !fingerprint.isEmpty() && remainingTicks > 0 && strength > 0.0F;
    }

    public StrainImmunity tick() {
        return new StrainImmunity(
                fingerprint, displayName, remainingTicks - 1, strength);
    }

    public static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null) return "";
        String normalized = fingerprint.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) normalized = normalized.substring(0, 64);
        return normalized.replaceAll("[^A-Z0-9_-]", "");
    }

    public static String sanitizeName(String name, String fallbackFingerprint) {
        String cleaned = name == null ? "" : name.strip();
        if (cleaned.length() > MAX_NAME_LENGTH) cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        return cleaned.isEmpty() ? fallbackFingerprint : cleaned;
    }
}
