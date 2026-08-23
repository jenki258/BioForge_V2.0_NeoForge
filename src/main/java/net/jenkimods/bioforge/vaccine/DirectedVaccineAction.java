package net.jenkimods.bioforge.vaccine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record DirectedVaccineAction(
        ResourceLocation id,
        Set<VaccineTargetCategory> categories,
        String operation,
        boolean requireSamePathogen,
        float minimumSimilarity,
        float baseSuccessChance,
        float potency,
        float neutralEpsilon,
        String targetOverride,
        String valueOverride,
        String neutralValueOverride
) {
    private static final Set<String> OPERATIONS = Set.of(
            "auto_opposite", "add", "remove", "toggle", "set",
            "increase", "reduce", "move_toward_neutral", "replace"
    );

    public DirectedVaccineAction {
        categories = Set.copyOf(categories);
        operation = operation.toLowerCase(Locale.ROOT);
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("A vaccine action requires at least one category");
        }
        if (!OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("Unsupported directed vaccine operation: " + operation);
        }
        minimumSimilarity = Mth.clamp(minimumSimilarity, 0.0f, 1.0f);
        baseSuccessChance = Mth.clamp(baseSuccessChance, 0.0f, 1.0f);
        potency = Math.max(0.0f, potency);
        neutralEpsilon = Math.max(0.0f, neutralEpsilon);
        targetOverride = targetOverride == null ? "" : targetOverride.trim();
        valueOverride = valueOverride == null ? "" : valueOverride.trim();
        neutralValueOverride = neutralValueOverride == null
                ? "" : neutralValueOverride.trim();
    }

    public static DirectedVaccineAction fromJson(ResourceLocation id, JsonObject json) {
        EnumSet<VaccineTargetCategory> categories = EnumSet.noneOf(VaccineTargetCategory.class);
        JsonElement categoriesJson = json.get("categories");
        if (categoriesJson == null) {
            VaccineTargetCategory category = VaccineTargetCategory.fromName(
                    GsonHelper.getAsString(json, "category"));
            if (category != null) categories.add(category);
        } else if (categoriesJson.isJsonArray()) {
            JsonArray array = categoriesJson.getAsJsonArray();
            for (JsonElement element : array) {
                VaccineTargetCategory category =
                        VaccineTargetCategory.fromName(element.getAsString());
                if (category == null) {
                    throw new IllegalArgumentException(
                            "Unknown directed vaccine category: " + element.getAsString());
                }
                categories.add(category);
            }
        } else {
            VaccineTargetCategory category =
                    VaccineTargetCategory.fromName(categoriesJson.getAsString());
            if (category != null) categories.add(category);
        }
        return new DirectedVaccineAction(
                id,
                categories,
                GsonHelper.getAsString(json, "operation", "auto_opposite"),
                GsonHelper.getAsBoolean(json, "require_same_pathogen", true),
                GsonHelper.getAsFloat(json, "minimum_similarity", 0.20f),
                GsonHelper.getAsFloat(json, "base_success_chance", 0.85f),
                GsonHelper.getAsFloat(json, "potency", 0.25f),
                GsonHelper.getAsFloat(json, "neutral_epsilon", 0.0001f),
                GsonHelper.getAsString(json, "target", ""),
                json.has("value") ? json.get("value").getAsString() : "",
                json.has("neutral_value") ? json.get("neutral_value").getAsString() : ""
        );
    }

    public boolean supports(VaccineTargetCategory category) {
        return categories.contains(category);
    }
}
