package net.jenkimods.bioforge.world.incubator;

import com.google.gson.*;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.PathogenType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.*;

@EventBusSubscriber(modid = BioForge.MODID)
public class CatalystMappingManager extends SimpleJsonResourceReloadListener {

    public static final CatalystMappingManager INSTANCE = new CatalystMappingManager();
    private static final double DEFAULT_UNIVERSAL_CHANCE = 0.025D;
    private static final PathogenType[] STANDARD_PATHOGENS = {
            PathogenType.VIRUS,
            PathogenType.BACTERIA,
            PathogenType.FUNGI,
            PathogenType.PARASITE,
            PathogenType.PRION
    };
    private Map<Item, PathogenType> itemToPathogen = new HashMap<>();
    private Map<Item, ResourceLocation> itemToPathogenIds = new HashMap<>();
    private final Map<Item, ResourceLocation> javaMappings = new LinkedHashMap<>();
    private double netherStarUniversalChance = DEFAULT_UNIVERSAL_CHANCE;
    private Double javaUniversalChance;
    private boolean javaRegistrationsFrozen;

    private CatalystMappingManager() {
        super(new Gson(), "incubator/catalyst_mappings");
    }

    @Nullable
    public PathogenType getPathogen(Item item) {
        return itemToPathogen.get(item);
    }

    @Nullable
    public ResourceLocation getPathogenId(Item item) {
        return itemToPathogenIds.get(item);
    }

    public PathogenType getRandomPathogen() {
        Random random = java.util.concurrent.ThreadLocalRandom.current();
        if (random.nextDouble() < netherStarUniversalChance) {
            return PathogenType.UNIVERSAL;
        }
        return STANDARD_PATHOGENS[random.nextInt(STANDARD_PATHOGENS.length)];
    }

    public ResourceLocation getRandomPathogenId() {
        return BioForgeIds.pathogen(getRandomPathogen());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, PathogenType> map = new HashMap<>();
        Map<Item, ResourceLocation> idMap = new HashMap<>();
        double[] universalChance = { DEFAULT_UNIVERSAL_CHANCE };
        boolean[] universalChanceConfigured = { false };
        objects.forEach((id, element) -> {
            try {
                JsonObject root = element.getAsJsonObject();
                if (root.has("nether_star_universal_chance")) {
                    universalChance[0] = Math.max(0.0D, Math.min(1.0D,
                            root.get("nether_star_universal_chance").getAsDouble()));
                    universalChanceConfigured[0] = true;
                }
                if (root.has("mappings")) {
                    JsonArray arr = root.getAsJsonArray("mappings");
                    for (JsonElement e : arr) {
                        JsonObject entry = e.getAsJsonObject();
                        String itemId = entry.get("item").getAsString();
                        String pathogenName = entry.get("pathogen").getAsString();
                        ResourceLocation parsedItemId = ResourceLocation.tryParse(itemId);
                        Item item = parsedItemId == null ? null
                                : BuiltInRegistries.ITEM.getOptional(parsedItemId).orElse(null);
                        ResourceLocation pathogenId = BioForgeIds.parse(pathogenName);
                        if (item != null) {
                            idMap.put(item, pathogenId);
                            PathogenType pathogen = BioForgeIds.legacyPathogen(pathogenId);
                            map.put(item, pathogen == null ? PathogenType.UNIVERSAL : pathogen);
                        }
                    }
                }
            } catch (Exception ex) {
                BioForge.LOGGER.error("Error loading catalyst mapping: {}", ex.getMessage());
            }
        });
        javaMappings.forEach((item, pathogenId) -> {
            if (idMap.putIfAbsent(item, pathogenId) == null) {
                PathogenType pathogen = BioForgeIds.legacyPathogen(pathogenId);
                map.put(item, pathogen == null ? PathogenType.UNIVERSAL : pathogen);
            }
        });
        if (javaUniversalChance != null && !universalChanceConfigured[0]) {
            universalChance[0] = javaUniversalChance;
        }
        this.itemToPathogen = map;
        this.itemToPathogenIds = idMap;
        this.netherStarUniversalChance = universalChance[0];
    }

    public synchronized void registerJava(Item item, PathogenType pathogen) {
        if (pathogen == null) throw new IllegalArgumentException("Catalyst pathogen cannot be null");
        registerJava(item, BioForgeIds.pathogen(pathogen));
    }

    public synchronized void registerJava(Item item, ResourceLocation pathogenId) {
        if (javaRegistrationsFrozen) throw new IllegalStateException("Catalyst mapping registry is frozen");
        if (item == null || pathogenId == null) throw new IllegalArgumentException("Catalyst mapping cannot be null");
        if (BioForgeDefinitionManager.pathogen(pathogenId).isEmpty()) {
            throw new IllegalArgumentException("Unknown catalyst pathogen " + pathogenId);
        }
        if (javaMappings.putIfAbsent(item, pathogenId) != null) {
            throw new IllegalArgumentException("Duplicate Java catalyst mapping for " + item);
        }
    }

    public synchronized void setJavaUniversalChance(double chance) {
        if (javaRegistrationsFrozen) throw new IllegalStateException("Catalyst mapping registry is frozen");
        javaUniversalChance = Math.max(0.0D, Math.min(1.0D, chance));
    }

    public synchronized void freezeJavaRegistrations() {
        javaRegistrationsFrozen = true;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public Map<Item, PathogenType> getAllMappings() {
        return Collections.unmodifiableMap(itemToPathogen);
    }

    public Map<Item, ResourceLocation> getAllMappingIds() {
        return Collections.unmodifiableMap(itemToPathogenIds);
    }
}
