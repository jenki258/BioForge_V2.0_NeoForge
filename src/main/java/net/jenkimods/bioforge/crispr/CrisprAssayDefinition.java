package net.jenkimods.bioforge.crispr;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public record CrisprAssayDefinition(
        ResourceLocation id,
        int minimumGrowth,
        int cultureCost,
        boolean showNumericScore
) {
    public CrisprAssayDefinition {
        minimumGrowth = Math.max(0, minimumGrowth);
        cultureCost = Math.max(0, cultureCost);
    }

    public static CrisprAssayDefinition fromJson(ResourceLocation id, JsonObject json) {
        return new CrisprAssayDefinition(
                id,
                GsonHelper.getAsInt(json, "minimum_growth", 1),
                GsonHelper.getAsInt(json, "culture_cost", 1),
                GsonHelper.getAsBoolean(json, "show_numeric_score", true)
        );
    }
}
