package net.jenkimods.bioforge.api.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ResearchJournalPageDefinition(
        ResourceLocation id,
        int order,
        Component title,
        List<Element> elements,
        List<UnlockRequirement> unlockRequirements,
        boolean requireAllUnlocks,
        List<ResearchJournalRecipeReference> recipes
) {
    public record Element(Component component, @Nullable ResourceLocation linkTarget,
                          int lineBreaks) {
        public Element {
            Objects.requireNonNull(component, "component");
            if (lineBreaks < 0 || lineBreaks > 8) {
                throw new IllegalArgumentException("Journal line_breaks must be between 0 and 8");
            }
        }
    }

    public record UnlockRequirement(String value) {
        public UnlockRequirement {
            value = Objects.requireNonNull(value, "value").trim();
            String raw = value.startsWith("#") ? value.substring(1) : value;
            if (ResourceLocation.tryParse(raw) == null) {
                throw new IllegalArgumentException("Invalid journal unlock item or tag " + value);
            }
        }

        public boolean test(ItemStack stack) {
            if (stack.isEmpty()) return false;
            String raw = value.startsWith("#") ? value.substring(1) : value;
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) return false;
            if (value.startsWith("#")) return stack.is(ItemTags.create(id));
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            return item != null && stack.is(item);
        }
    }

    public ResearchJournalPageDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        elements = List.copyOf(elements);
        unlockRequirements = List.copyOf(unlockRequirements);
        recipes = List.copyOf(recipes);
    }

    public ResearchJournalPageDefinition(ResourceLocation id, int order,
                                         Component title, List<Element> elements) {
        this(id, order, title, elements, List.of(), false, List.of());
    }

    public ResearchJournalPageDefinition(ResourceLocation id, int order,
                                         Component title, List<Element> elements,
                                         List<UnlockRequirement> unlockRequirements,
                                         boolean requireAllUnlocks) {
        this(id, order, title, elements, unlockRequirements, requireAllUnlocks,
                List.of());
    }

    public static ResearchJournalPageDefinition fromJson(ResourceLocation id,
                                                           JsonObject json,
                                                           HolderLookup.Provider registries) {
        int order = GsonHelper.getAsInt(json, "order", 0);
        Component title = readComponent(json.get("title"), "title", registries);
        JsonArray content = GsonHelper.getAsJsonArray(json, "content");
        List<Element> elements = new ArrayList<>(content.size());
        for (JsonElement raw : content) {
            if (!raw.isJsonObject()) {
                throw new IllegalArgumentException("Journal content entries must be objects");
            }
            JsonObject entry = raw.getAsJsonObject();
            Component component = readComponent(
                    entry.get("component"), "content.component", registries);
            ResourceLocation link = entry.has("link")
                    ? BioForgeIds.parse(GsonHelper.getAsString(entry, "link")) : null;
            elements.add(new Element(component, link,
                    GsonHelper.getAsInt(entry, "line_breaks", 1)));
        }
        List<UnlockRequirement> requirements = new ArrayList<>();
        if (json.has("unlock_items")) {
            for (JsonElement raw : GsonHelper.getAsJsonArray(json, "unlock_items")) {
                requirements.add(new UnlockRequirement(raw.getAsString()));
            }
        }
        String mode = GsonHelper.getAsString(json, "unlock_mode", "any");
        if (!mode.equals("any") && !mode.equals("all")) {
            throw new IllegalArgumentException("Journal unlock_mode must be 'any' or 'all'");
        }
        List<ResearchJournalRecipeReference> recipes = new ArrayList<>();
        if (json.has("recipes")) {
            for (JsonElement raw : GsonHelper.getAsJsonArray(json, "recipes")) {
                recipes.add(ResearchJournalRecipeReference.fromJson(raw));
            }
        }
        return new ResearchJournalPageDefinition(id, order, title, elements,
                requirements, mode.equals("all"), recipes);
    }

    private static Component readComponent(@Nullable JsonElement element, String field,
                                           HolderLookup.Provider registries) {
        if (element == null) {
            throw new IllegalArgumentException("Journal page is missing '" + field + "'");
        }
        Component component = Component.Serializer.fromJson(element, registries);
        if (component == null) {
            throw new IllegalArgumentException("Invalid component in journal field '" + field + "'");
        }
        return component;
    }

    public static Builder builder(ResourceLocation id, Component title) {
        return new Builder(id, title);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final Component title;
        private final List<Element> elements = new ArrayList<>();
        private final List<UnlockRequirement> unlockRequirements = new ArrayList<>();
        private final List<ResearchJournalRecipeReference> recipes = new ArrayList<>();
        private int order;
        private boolean requireAllUnlocks;

        private Builder(ResourceLocation id, Component title) {
            this.id = Objects.requireNonNull(id, "id");
            this.title = Objects.requireNonNull(title, "title");
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder text(Component component, int lineBreaks) {
            elements.add(new Element(component, null, lineBreaks));
            return this;
        }

        public Builder link(Component component, ResourceLocation target, int lineBreaks) {
            elements.add(new Element(component, Objects.requireNonNull(target, "target"),
                    lineBreaks));
            return this;
        }

        public Builder unlockWith(ResourceLocation itemId) {
            unlockRequirements.add(new UnlockRequirement(itemId.toString()));
            return this;
        }

        public Builder unlockWithTag(ResourceLocation tagId) {
            unlockRequirements.add(new UnlockRequirement("#" + tagId));
            return this;
        }

        public Builder requireAllUnlocks() {
            requireAllUnlocks = true;
            return this;
        }

        public Builder recipe(ResourceLocation recipeId) {
            recipes.add(new ResearchJournalRecipeReference(
                    ResearchJournalRecipeReference.Type.CRAFTING,
                    Objects.requireNonNull(recipeId, "recipeId")));
            return this;
        }

        public Builder laboratoryRecipe(ResourceLocation recipeId) {
            recipes.add(new ResearchJournalRecipeReference(
                    ResearchJournalRecipeReference.Type.LABORATORY,
                    Objects.requireNonNull(recipeId, "recipeId")));
            return this;
        }

        public Builder vaccineMakerRecipe(ResourceLocation recipeId) {
            recipes.add(new ResearchJournalRecipeReference(
                    ResearchJournalRecipeReference.Type.VACCINE_MAKER,
                    Objects.requireNonNull(recipeId, "recipeId")));
            return this;
        }

        public ResearchJournalPageDefinition build() {
            return new ResearchJournalPageDefinition(id, order, title, elements,
                    unlockRequirements, requireAllUnlocks, recipes);
        }
    }
}
