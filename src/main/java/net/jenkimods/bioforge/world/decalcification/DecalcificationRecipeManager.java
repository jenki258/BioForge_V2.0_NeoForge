package net.jenkimods.bioforge.world.decalcification;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.recipe.BioForgeRecipeRegistration;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class DecalcificationRecipeManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final DecalcificationRecipeManager INSTANCE = new DecalcificationRecipeManager();

    private final List<DecalcificationRecipe> recipes = new ArrayList<>();
    private final Map<ResourceLocation, DecalcificationRecipe> javaRecipes = new LinkedHashMap<>();
    private boolean javaRegistrationsFrozen;

    private DecalcificationRecipeManager() {
        super(GSON, "decalcification");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        recipes.clear();
        var loadedIds = new LinkedHashSet<ResourceLocation>();
        elements.forEach((id, element) -> {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(element, "decalcification recipe");
                String inputStr = GsonHelper.getAsString(json, "input");
                String outputStr = GsonHelper.getAsString(json, "output");
                boolean copyBloodData = GsonHelper.getAsBoolean(json, "copy_blood_data", true);
                boolean copyNbt = GsonHelper.getAsBoolean(json, "copy_nbt", false);
                JsonArray keysArray = GsonHelper.getAsJsonArray(json, "copy_nbt_keys", new JsonArray());
                List<String> keys = StreamSupport.stream(keysArray.spliterator(), false)
                        .map(JsonElement::getAsString)
                        .toList();
                recipes.add(new DecalcificationRecipe(inputStr, outputStr, copyBloodData, copyNbt, keys));
                loadedIds.add(id);
            } catch (Exception ex) {
                BioForge.LOGGER.error("Failed to parse decalcification recipe {}: {}", id, ex.getMessage());
            }
        });
        javaRecipes.forEach((id, recipe) -> {
            if (!loadedIds.contains(id)) recipes.add(recipe);
        });
        BioForge.LOGGER.info("Loaded {} decalcification recipes", recipes.size());
    }

    public synchronized void registerJava(ResourceLocation id, DecalcificationRecipe recipe) {
        if (javaRegistrationsFrozen) throw new IllegalStateException("Decalcification recipe registry is frozen");
        if (id == null || recipe == null) throw new IllegalArgumentException("Decalcification recipe cannot be null");
        if (javaRecipes.putIfAbsent(id, recipe) != null) {
            throw new IllegalArgumentException("Duplicate Java decalcification recipe " + id);
        }
    }

    public synchronized void freezeJavaRegistrations() {
        javaRegistrationsFrozen = true;
    }

    public Optional<DecalcificationRecipe> getRecipe(Level level, ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        if (level != null) {
            for (var holder : level.getRecipeManager().getAllRecipesFor(
                    BioForgeRecipeRegistration.DECALCIFICATION_TYPE)) {
                DecalcificationRecipe recipe = holder.value().recipe();
                if (matchesInput(recipe.input(), input)) return Optional.of(recipe);
            }
        }
        for (DecalcificationRecipe recipe : recipes) {
            if (matchesInput(recipe.input(), input)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    private boolean matchesInput(String inputStr, ItemStack stack) {
        if (inputStr.startsWith("#")) {
            ResourceLocation inputId = ResourceLocation.tryParse(inputStr.substring(1));
            if (inputId == null) return false;
            TagKey<Item> tag = ItemTags.create(inputId);
            return stack.is(tag);
        } else {
            ResourceLocation inputId = ResourceLocation.tryParse(inputStr);
            if (inputId == null) return false;
            Item item = BuiltInRegistries.ITEM.getOptional(inputId).orElse(null);
            return item != null && stack.is(item);
        }
    }

    public Item resolveOutput(DecalcificationRecipe recipe, RandomSource random) {
        String outputStr = recipe.output();
        if (outputStr.startsWith("#")) {
            ResourceLocation loc = ResourceLocation.tryParse(outputStr.substring(1));
            if (loc == null) return null;
            List<Item> items = StreamSupport.stream(
                            BuiltInRegistries.ITEM.getTagOrEmpty(
                                    ItemTags.create(loc)).spliterator(), false)
                    .map(Holder::value)
                    .toList();
            if (items.isEmpty()) return null;
            return items.get(random.nextInt(items.size()));
        } else {
            ResourceLocation loc = ResourceLocation.tryParse(outputStr);
            if (loc == null) return null;
            return BuiltInRegistries.ITEM.getOptional(loc).orElse(null);
        }
    }

    public List<DecalcificationRecipe> getRecipes() {
        return Collections.unmodifiableList(recipes);
    }

    public List<DecalcificationRecipe> getRecipes(Level level) {
        if (level == null) return getRecipes();
        List<DecalcificationRecipe> combined = new ArrayList<>();
        level.getRecipeManager().getAllRecipesFor(BioForgeRecipeRegistration.DECALCIFICATION_TYPE)
                .forEach(holder -> combined.add(holder.value().recipe()));
        combined.addAll(recipes);
        return List.copyOf(combined);
    }
}
