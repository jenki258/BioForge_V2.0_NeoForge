package net.jenkimods.bioforge.world.laboratory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.recipe.BioForgeRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = BioForge.MODID)
public final class LaboratoryProcessRecipeManager extends SimpleJsonResourceReloadListener {
    public static final LaboratoryProcessRecipeManager INSTANCE = new LaboratoryProcessRecipeManager();
    private final Map<ResourceLocation, LaboratoryProcessRecipe> javaRecipes = new LinkedHashMap<>();
    private List<LaboratoryProcessRecipe> recipes = List.of();
    private boolean frozen;

    private LaboratoryProcessRecipeManager() {
        super(new Gson(), "laboratory_processing");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, LaboratoryProcessRecipe> loaded = new LinkedHashMap<>(javaRecipes);
        objects.forEach((id, json) -> {
            try {
                JsonObject root = json.getAsJsonObject();
                if (root.has("recipes")) {
                    int index = 0;
                    for (JsonElement element : root.getAsJsonArray("recipes")) {
                        JsonObject entry = element.getAsJsonObject();
                        String name = GsonHelper.getAsString(entry, "id", Integer.toString(index++));
                        ResourceLocation recipeId = ResourceLocation.tryBuild(id.getNamespace(), id.getPath() + "/" + name);
                        loaded.put(recipeId, parseRecipe(recipeId, entry));
                    }
                } else {
                    loaded.put(id, parseRecipe(id, root));
                }
            } catch (Exception exception) {
                BioForge.LOGGER.error("Unable to load laboratory recipe {}: {}", id, exception.getMessage());
            }
        });
        recipes = List.copyOf(loaded.values());
        BioForge.LOGGER.info("Loaded {} laboratory processing recipes", recipes.size());
    }

    public static LaboratoryProcessRecipe parseRecipe(ResourceLocation id, JsonObject root) {
        LaboratoryStation station = LaboratoryStation.byName(GsonHelper.getAsString(root, "station"));
        List<Ingredient> ingredients = new ArrayList<>();
        root.getAsJsonArray("ingredients").forEach(element -> ingredients.add(
                Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, element)
                        .getOrThrow(IllegalArgumentException::new)));
        if (ingredients.isEmpty() || ingredients.size() > station.inputSlots()) {
            throw new IllegalArgumentException("Recipe for " + station.getSerializedName()
                    + " must contain 1-" + station.inputSlots() + " ingredients");
        }
        if (station.processesInPlace() && ingredients.size() != 1) {
            throw new IllegalArgumentException(
                    "Sterilization recipes must contain exactly one ingredient");
        }
        Item output = resolveItem(GsonHelper.getAsString(root, "output"));
        int count = GsonHelper.getAsInt(root, "count", 1);
        ItemStack waste = ItemStack.EMPTY;
        if (root.has("waste")) {
            Item wasteItem = resolveItem(GsonHelper.getAsString(root, "waste"));
            int wasteCount = GsonHelper.getAsInt(root, "waste_count", 1);
            waste = new ItemStack(wasteItem,
                    Math.max(1, Math.min(wasteItem.getDefaultMaxStackSize(), wasteCount)));
        }
        if (station == LaboratoryStation.PHARMA_MIXER && waste.isEmpty()) {
            throw new IllegalArgumentException("Pharma Mixer recipes must define a waste output");
        }
        int time = GsonHelper.getAsInt(root, "processing_time", 160);
        return new LaboratoryProcessRecipe(id, station, ingredients,
                new ItemStack(output, Math.max(1,
                        Math.min(output.getDefaultMaxStackSize(), count))),
                waste, GsonHelper.getAsBoolean(root, "copy_nbt", false), time);
    }

    private static Item resolveItem(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        Item item = id == null ? null
                : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalArgumentException("Unknown output item " + id);
        }
        return item;
    }

    public Optional<LaboratoryProcessRecipe> find(Level level, LaboratoryStation station,
                                                   ItemStackHandler inventory) {
        return recipes(level).stream().filter(recipe -> recipe.station() == station
                && recipe.matches(inventory)).findFirst();
    }

    public Optional<LaboratoryProcessRecipe> findSingle(Level level, LaboratoryStation station,
                                                         ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        return recipes(level).stream().filter(recipe -> recipe.station() == station
                && recipe.matchesSingle(input)).findFirst();
    }

    public synchronized void registerJava(LaboratoryProcessRecipe recipe) {
        if (frozen) throw new IllegalStateException("Laboratory recipe registry is frozen");
        if (javaRecipes.putIfAbsent(recipe.id(), recipe) != null) {
            throw new IllegalArgumentException("Duplicate laboratory recipe " + recipe.id());
        }
    }

    public synchronized void freezeJavaRegistrations() {
        frozen = true;
    }

    public List<LaboratoryProcessRecipe> recipes() {
        return recipes;
    }

    public List<LaboratoryProcessRecipe> recipes(Level level) {
        if (level == null) return recipes();
        List<LaboratoryProcessRecipe> combined = new ArrayList<>();
        level.getRecipeManager().getAllRecipesFor(BioForgeRecipeRegistration.LABORATORY_TYPE)
                .forEach(holder -> combined.add(holder.value().recipe()
                        .withId(holder.id())));
        combined.addAll(recipes);
        return List.copyOf(combined);
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}
