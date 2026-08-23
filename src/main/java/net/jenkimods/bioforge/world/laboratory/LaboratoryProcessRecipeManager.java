package net.jenkimods.bioforge.world.laboratory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = BioForge.MODID)
public final class LaboratoryProcessRecipeManager
        extends SimpleJsonResourceReloadListener {

    public static final LaboratoryProcessRecipeManager INSTANCE =
            new LaboratoryProcessRecipeManager();

    private final Map<ResourceLocation, LaboratoryProcessRecipe> javaRecipes =
            new LinkedHashMap<>();

    private List<LaboratoryProcessRecipe> recipes = List.of();

    private HolderLookup.Provider registryLookup;

    private boolean frozen;

    private LaboratoryProcessRecipeManager() {
        super(new Gson(), "laboratory_processing");
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> objects,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, LaboratoryProcessRecipe> loaded =
                new LinkedHashMap<>(javaRecipes);

        if (registryLookup == null) {
            BioForge.LOGGER.error(
                    "Laboratory recipes cannot be loaded: registry lookup is unavailable"
            );

            recipes = List.copyOf(loaded.values());
            return;
        }

        objects.forEach((id, json) -> {
            try {
                JsonObject root = json.getAsJsonObject();

                if (root.has("recipes")) {
                    int index = 0;

                    for (JsonElement element :
                            root.getAsJsonArray("recipes")) {

                        JsonObject entry =
                                element.getAsJsonObject();

                        String name =
                                GsonHelper.getAsString(
                                        entry,
                                        "id",
                                        Integer.toString(index++)
                                );

                        ResourceLocation recipeId =
                                ResourceLocation.tryBuild(
                                        id.getNamespace(),
                                        id.getPath() + "/" + name
                                );

                        if (recipeId == null) {
                            throw new IllegalArgumentException(
                                    "Invalid laboratory recipe id for " + id
                            );
                        }

                        loaded.put(
                                recipeId,
                                parse(recipeId, entry)
                        );
                    }

                } else {

                    loaded.put(
                            id,
                            parse(id, root)
                    );
                }

            } catch (Exception exception) {
                BioForge.LOGGER.error(
                        "Unable to load laboratory recipe {}: {}",
                        id,
                        exception.getMessage(),
                        exception
                );
            }
        });

        recipes = List.copyOf(loaded.values());

        BioForge.LOGGER.info(
                "Loaded {} laboratory processing recipes",
                recipes.size()
        );
    }

    private LaboratoryProcessRecipe parse(
            ResourceLocation id,
            JsonObject root
    ) {
        LaboratoryStation station =
                LaboratoryStation.byName(
                        GsonHelper.getAsString(root, "station")
                );

        RegistryOps<JsonElement> ops =
                RegistryOps.create(
                        JsonOps.INSTANCE,
                        registryLookup
                );

        List<Ingredient> ingredients =
                new ArrayList<>();

        for (JsonElement element :
                root.getAsJsonArray("ingredients")) {

            Ingredient ingredient =
                    Ingredient.CODEC_NONEMPTY
                            .parse(ops, element)
                            .getOrThrow(message ->
                                    new IllegalArgumentException(
                                            "Failed to parse ingredient "
                                                    + element
                                                    + ": "
                                                    + message
                                    )
                            );

            ingredients.add(ingredient);
        }

        if (ingredients.isEmpty()
                || ingredients.size() > station.inputSlots()) {

            throw new IllegalArgumentException(
                    "Recipe for "
                            + station.getSerializedName()
                            + " must contain 1-"
                            + station.inputSlots()
                            + " ingredients"
            );
        }

        if (station.processesInPlace()
                && ingredients.size() != 1) {

            throw new IllegalArgumentException(
                    "Sterilization recipes must contain exactly one ingredient"
            );
        }

        Item output =
                resolveItem(
                        GsonHelper.getAsString(root, "output")
                );

        int count =
                GsonHelper.getAsInt(
                        root,
                        "count",
                        1
                );

        ItemStack waste =
                ItemStack.EMPTY;

        if (root.has("waste")) {

            Item wasteItem =
                    resolveItem(
                            GsonHelper.getAsString(
                                    root,
                                    "waste"
                            )
                    );

            int wasteCount =
                    GsonHelper.getAsInt(
                            root,
                            "waste_count",
                            1
                    );

            waste =
                    new ItemStack(
                            wasteItem,
                            Math.max(
                                    1,
                                    Math.min(
                                            wasteItem
                                                    .getDefaultMaxStackSize(),
                                            wasteCount
                                    )
                            )
                    );
        }

        if (station == LaboratoryStation.PHARMA_MIXER
                && waste.isEmpty()) {

            throw new IllegalArgumentException(
                    "Pharma Mixer recipes must define a waste output"
            );
        }

        int time =
                GsonHelper.getAsInt(
                        root,
                        "processing_time",
                        160
                );

        ItemStack result =
                new ItemStack(
                        output,
                        Math.max(
                                1,
                                Math.min(
                                        output.getDefaultMaxStackSize(),
                                        count
                                )
                        )
                );

        return new LaboratoryProcessRecipe(
                id,
                station,
                ingredients,
                result,
                waste,
                GsonHelper.getAsBoolean(
                        root,
                        "copy_nbt",
                        false
                ),
                time
        );
    }

    private static Item resolveItem(String value) {
        ResourceLocation id =
                ResourceLocation.tryParse(value);

        Item item =
                id == null
                        ? null
                        : BuiltInRegistries.ITEM
                        .getOptional(id)
                        .orElse(null);

        if (item == null
                || item == net.minecraft.world.item.Items.AIR) {

            throw new IllegalArgumentException(
                    "Unknown output item " + id
            );
        }

        return item;
    }

    public Optional<LaboratoryProcessRecipe> find(
            LaboratoryStation station,
            ItemStackHandler inventory
    ) {
        return recipes.stream()
                .filter(recipe ->
                        recipe.station() == station
                                && recipe.matches(inventory)
                )
                .findFirst();
    }

    public Optional<LaboratoryProcessRecipe> findSingle(
            LaboratoryStation station,
            ItemStack input
    ) {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        return recipes.stream()
                .filter(recipe ->
                        recipe.station() == station
                                && recipe.matchesSingle(input)
                )
                .findFirst();
    }

    public synchronized void registerJava(
            LaboratoryProcessRecipe recipe
    ) {
        if (frozen) {
            throw new IllegalStateException(
                    "Laboratory recipe registry is frozen"
            );
        }

        if (javaRecipes.putIfAbsent(
                recipe.id(),
                recipe
        ) != null) {

            throw new IllegalArgumentException(
                    "Duplicate laboratory recipe "
                            + recipe.id()
            );
        }
    }

    public synchronized void freezeJavaRegistrations() {
        frozen = true;
    }

    public List<LaboratoryProcessRecipe> recipes() {
        return recipes;
    }

    @SubscribeEvent
    public static void addReloadListener(
            AddReloadListenerEvent event
    ) {
        INSTANCE.registryLookup =
                event.getServerResources()
                        .getRegistryLookup();

        event.addListener(INSTANCE);
    }
}