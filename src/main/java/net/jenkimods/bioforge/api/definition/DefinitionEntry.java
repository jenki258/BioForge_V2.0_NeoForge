package net.jenkimods.bioforge.api.definition;

import org.jetbrains.annotations.Nullable;

public record DefinitionEntry<T>(
        @Nullable T definition,
        int priority,
        boolean replace,
        boolean enabled,
        DefinitionSource source
) {
    public DefinitionEntry {
        if (enabled && definition == null) {
            throw new IllegalArgumentException("Enabled registry entry requires a definition");
        }
    }

    public static <T> DefinitionEntry<T> enabled(
            T definition, int priority, boolean replace, DefinitionSource source) {
        return new DefinitionEntry<>(definition, priority, replace, true, source);
    }

    public static <T> DefinitionEntry<T> disabled(
            int priority, boolean replace, DefinitionSource source) {
        return new DefinitionEntry<>(null, priority, replace, false, source);
    }
}
