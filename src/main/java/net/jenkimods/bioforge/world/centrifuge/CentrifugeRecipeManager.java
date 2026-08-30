package net.jenkimods.bioforge.world.centrifuge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.recipe.BioForgeRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class CentrifugeRecipeManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final CentrifugeRecipeManager INSTANCE = new CentrifugeRecipeManager();
    private final List<CentrifugeRecipe> recipes = new ArrayList<>();
    private final Map<ResourceLocation, CentrifugeRecipe> javaRecipes = new LinkedHashMap<>();
    private boolean javaRegistrationsFrozen;

    private CentrifugeRecipeManager() {
        super(GSON, "centrifuge");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        recipes.clear();
        var loadedIds = new LinkedHashSet<ResourceLocation>();
        for (var entry : elements.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "centrifuge recipe");

                String inputStr = GsonHelper.getAsString(json, "input");
                CentrifugeIngredient input = CentrifugeIngredient.parse(inputStr);

                List<CentrifugeOutput> outputList = new ArrayList<>();
                CentrifugeIngredient singleOutput = null;
                if (json.has("outputs")) {
                    JsonArray outs = GsonHelper.getAsJsonArray(json, "outputs");
                    for (JsonElement e : outs) {
                        JsonObject outObj = e.getAsJsonObject();
                        String outStr = GsonHelper.getAsString(outObj, "item");
                        int weight = GsonHelper.getAsInt(outObj, "weight", 1);
                        outputList.add(new CentrifugeOutput(CentrifugeIngredient.parse(outStr), weight));
                    }
                } else if (json.has("output")) {
                    String outputStr = GsonHelper.getAsString(json, "output");
                    singleOutput = CentrifugeIngredient.parse(outputStr);
                } else {
                    throw new JsonParseException("Recipe must have 'output' or 'outputs'");
                }

                boolean copyBloodData = GsonHelper.getAsBoolean(json, "copy_blood_data", false);
                boolean copyNbt = GsonHelper.getAsBoolean(json, "copy_nbt", false);
                int processingTime = GsonHelper.getAsInt(json, "processing_time", 100);
                boolean copyInfection = GsonHelper.getAsBoolean(json, "copy_infection", false);
                JsonArray keysArray = GsonHelper.getAsJsonArray(json, "copy_nbt_keys", new JsonArray());
                List<String> keys = new ArrayList<>();
                for (JsonElement e : keysArray) keys.add(e.getAsString());

                recipes.add(new CentrifugeRecipe(input, singleOutput, outputList, copyBloodData, copyNbt, keys, copyInfection, processingTime));
                loadedIds.add(id);
            } catch (Exception ex) {
                BioForge.LOGGER.error("Failed to parse centrifuge recipe {}: {}", id, ex.getMessage());
            }
        }
        javaRecipes.forEach((id, recipe) -> {
            if (!loadedIds.contains(id)) recipes.add(recipe);
        });
        BioForge.LOGGER.info("Loaded {} centrifuge recipes", recipes.size());
    }

    public synchronized void registerJava(ResourceLocation id, CentrifugeRecipe recipe) {
        if (javaRegistrationsFrozen) throw new IllegalStateException("Centrifuge recipe registry is frozen");
        if (id == null || recipe == null) throw new IllegalArgumentException("Centrifuge recipe cannot be null");
        if (javaRecipes.putIfAbsent(id, recipe) != null) {
            throw new IllegalArgumentException("Duplicate Java centrifuge recipe " + id);
        }
    }

    public synchronized void freezeJavaRegistrations() {
        javaRegistrationsFrozen = true;
    }

    public Optional<CentrifugeRecipe> getRecipe(Level level, ItemStack inputStack) {
        if (inputStack.isEmpty()) return Optional.empty();
        if (level != null) {
            for (var holder : level.getRecipeManager().getAllRecipesFor(
                    BioForgeRecipeRegistration.CENTRIFUGE_TYPE)) {
                CentrifugeRecipe recipe = holder.value().recipe();
                if (recipe.input().test(inputStack)) return Optional.of(recipe);
            }
        }
        for (CentrifugeRecipe recipe : recipes) {
            if (recipe.input().test(inputStack)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    public List<CentrifugeRecipe> getRecipes() {
        return java.util.Collections.unmodifiableList(recipes);
    }

    public List<CentrifugeRecipe> getRecipes(Level level) {
        if (level == null) return getRecipes();
        List<CentrifugeRecipe> combined = new ArrayList<>();
        level.getRecipeManager().getAllRecipesFor(BioForgeRecipeRegistration.CENTRIFUGE_TYPE)
                .forEach(holder -> combined.add(holder.value().recipe()));
        combined.addAll(recipes);
        return List.copyOf(combined);
    }
}
