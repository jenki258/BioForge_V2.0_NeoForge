package net.jenkimods.bioforge.infection.lifecycle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.infection.InfectionLifecycleDefinition;
import net.minecraft.resources.ResourceLocation;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.InfectionData;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = BioForge.MODID)
public final class InfectionLifecycleRegistry extends SimpleJsonResourceReloadListener {
    private static final ResourceLocation DEFAULT_ID = ResourceLocation.tryBuild(BioForge.MODID, "default");
    public static final InfectionLifecycleRegistry INSTANCE = new InfectionLifecycleRegistry();
    private final Map<ResourceLocation, InfectionLifecycleDefinition> javaProfiles = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> javaPathogenDefaults = new LinkedHashMap<>();
    private Map<ResourceLocation, InfectionLifecycleDefinition> profiles = Map.of(DEFAULT_ID, fallback());
    private Map<ResourceLocation, ResourceLocation> pathogenDefaults = Map.of();
    private boolean frozen;

    private InfectionLifecycleRegistry() { super(new Gson(), "infection_lifecycle"); }

    public Optional<InfectionLifecycleDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public InfectionLifecycleDefinition resolve(ResourceLocation id) {
        InfectionLifecycleDefinition profile = profiles.get(id);
        if (profile == null) profile = profiles.get(DEFAULT_ID);
        return profile == null ? fallback() : profile;
    }

    public float infectivity(InfectionData data) {
        return data == null ? 1.0F : resolve(data.getLifecycle().profileId()).infectivity();
    }

    public float infectivity(StrainData strain) {
        return strain == null ? 1.0F : resolve(strain.getLifecycleProfileId()).infectivity();
    }

    public Map<ResourceLocation, InfectionLifecycleDefinition> all() {
        return Collections.unmodifiableMap(profiles);
    }

    public ResourceLocation profileForPathogen(ResourceLocation pathogenId) {
        return pathogenId == null ? DEFAULT_ID : pathogenDefaults.getOrDefault(pathogenId, DEFAULT_ID);
    }

    public synchronized void registerJava(InfectionLifecycleDefinition profile) {
        if (frozen) throw new IllegalStateException("Infection lifecycle registry is frozen");
        if (profile == null) throw new IllegalArgumentException("Lifecycle profile cannot be null");
        if (javaProfiles.putIfAbsent(profile.id(), profile) != null) {
            throw new IllegalArgumentException("Duplicate lifecycle profile " + profile.id());
        }
    }

    public synchronized void registerPathogenDefault(ResourceLocation pathogenId,
                                                     ResourceLocation profileId) {
        if (frozen) throw new IllegalStateException("Infection lifecycle registry is frozen");
        if (pathogenId == null || profileId == null) {
            throw new IllegalArgumentException("Pathogen lifecycle mapping cannot be null");
        }
        if (javaPathogenDefaults.putIfAbsent(pathogenId, profileId) != null) {
            throw new IllegalArgumentException("Duplicate lifecycle mapping for " + pathogenId);
        }
    }

    public synchronized void freezeJavaRegistrations() { frozen = true; }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, InfectionLifecycleDefinition> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> defaults = new LinkedHashMap<>();
        objects.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                ResourceLocation profileId = json.has("id")
                        ? ResourceLocation.tryParse(json.get("id").getAsString()) : id;
                if (profileId == null) throw new IllegalArgumentException("Invalid profile id");
                loaded.put(profileId, parse(profileId, json));
                if (json.has("pathogens")) {
                    for (JsonElement value : json.getAsJsonArray("pathogens")) {
                        ResourceLocation pathogen = ResourceLocation.tryParse(value.getAsString());
                        if (pathogen != null) defaults.put(pathogen, profileId);
                    }
                }
            } catch (RuntimeException exception) {
                BioForge.LOGGER.error("Failed to load infection lifecycle {}: {}", id, exception.getMessage());
            }
        });
        javaProfiles.forEach(loaded::putIfAbsent);
        javaPathogenDefaults.forEach(defaults::putIfAbsent);
        loaded.putIfAbsent(DEFAULT_ID, fallback());
        profiles = Map.copyOf(loaded);
        pathogenDefaults = Map.copyOf(defaults);
        BioForge.LOGGER.info("Loaded {} infection lifecycle profiles", profiles.size());
    }

    private static InfectionLifecycleDefinition parse(ResourceLocation id, JsonObject json) {
        return new InfectionLifecycleDefinition(id,
                integer(json, "incubation_ticks", 6000),
                decimal(json, "adaptation_speed", 0.35F),
                decimal(json, "hostile_climate_incubation_rate", 0.35F),
                decimal(json, "adaptation_points_per_second", 0.35F),
                decimal(json, "hot_adaptation_threshold", 100.0F),
                decimal(json, "cold_adaptation_threshold", 100.0F),
                text(json, "hot_adaptation_mutation", "heat_adaptation"),
                text(json, "cold_adaptation_mutation", "cold_adaptation"),
                text(json, "dual_adaptation_mutation", "thermal_homeostasis"),
                integer(json, "lifespan_ticks", -1),
                decimal(json, "infectivity", 1.0F),
                decimal(json, "cure_resistance", 0.0F),
                bool(json, "contagious_during_incubation", false));
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
    private static float decimal(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }
    private static String text(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static InfectionLifecycleDefinition fallback() {
        return new InfectionLifecycleDefinition(DEFAULT_ID, 6000, 0.35F, 0.35F,
                0.35F, 100.0F, 100.0F, "heat_adaptation", "cold_adaptation",
                "thermal_homeostasis", -1, 1.0F, 0.0F, false);
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}
