package net.jenkimods.bioforge.mutation;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;




public record MutationVisual(
        String id,
        String nameKey,
        String fallbackName,
        String rarity,
    @Nullable ResourceLocation icon
) {
    public static MutationVisual fromDefinition(MutationDefinition definition) {
        ResourceLocation safeIcon = definition.icon();
        if (safeIcon != null && safeIcon.toString().length() > 256) safeIcon = null;
        return new MutationVisual(
                truncate(definition.id(), 256),
                truncate(definition.nameKey(), 256),
                truncate(definition.name(), 160),
                truncate(definition.rarity(), 32),
                safeIcon
        );
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null) return "";
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
