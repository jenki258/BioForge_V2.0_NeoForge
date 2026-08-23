package net.jenkimods.bioforge.crispr;

import net.jenkimods.bioforge.mutation.MutationLoader;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class CrisprDisplayNames {
    private CrisprDisplayNames() {}

    public static Component target(VaccineTargetCategory category, String target) {
        if (category == null || target == null || target.isBlank()) {
            return Component.translatable("gui.bioforge.research.unknown");
        }
        return switch (category) {
            case MUTATION -> MutationLoader.INSTANCE.getMutation(target)
                    .map(definition -> Component.translatable(definition.nameKey()))
                    .orElseGet(() -> Component.translatable(
                            mutationTranslationKey(target)));
            case TRANSMISSION -> Component.translatable(
                    "infection_type.bioforge." + target.toLowerCase(Locale.ROOT));
            case SYMPTOM -> Component.translatable(
                    "microscope.symptom." + target.toLowerCase(Locale.ROOT));
        };
    }

    public static String humanize(String value) {
        String path = value;
        int separator = path.indexOf(':');
        if (separator >= 0 && separator + 1 < path.length()) {
            path = path.substring(separator + 1);
        }
        String[] words = path.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.isEmpty() ? value : result.toString();
    }

    public static String mutationTranslationKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        String namespace = separator >= 0 ? normalized.substring(0, separator) : "bioforge";
        String path = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return "mutation." + namespace + "." + path.replace('/', '.') + ".name";
    }
}
