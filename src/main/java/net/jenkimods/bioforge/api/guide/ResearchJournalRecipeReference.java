package net.jenkimods.bioforge.api.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;
import java.util.Objects;

public record ResearchJournalRecipeReference(Type type, ResourceLocation id) {
    public ResearchJournalRecipeReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static ResearchJournalRecipeReference fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new ResearchJournalRecipeReference(
                    Type.CRAFTING, BioForgeIds.parse(element.getAsString()));
        }
        JsonObject json = GsonHelper.convertToJsonObject(
                element, "research journal recipe reference");
        Type type = Type.fromName(GsonHelper.getAsString(json, "type", "crafting"));
        return new ResearchJournalRecipeReference(
                type, BioForgeIds.parse(GsonHelper.getAsString(json, "id")));
    }

    public enum Type {
        CRAFTING,
        LABORATORY,
        VACCINE_MAKER;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type fromName(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown Research Tablet recipe type: " + value);
            }
        }
    }
}
