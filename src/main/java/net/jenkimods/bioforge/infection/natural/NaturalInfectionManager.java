package net.jenkimods.bioforge.infection.natural;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.infection.NaturalInfectionRule;
import net.jenkimods.bioforge.api.infection.NaturalStrainDefinition;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = BioForge.MODID)
public final class NaturalInfectionManager extends SimpleJsonResourceReloadListener {
    private static final String CHECKED_TAG = "BioForgeNaturalInfectionChecked";
    private static final int CHECK_VERSION = 3;
    public static final NaturalInfectionManager INSTANCE = new NaturalInfectionManager();
    private final Map<ResourceLocation, NaturalStrainDefinition> javaStrains = new LinkedHashMap<>();
    private final Map<ResourceLocation, NaturalInfectionRule> javaRules = new LinkedHashMap<>();
    private Map<ResourceLocation, NaturalStrainDefinition> strains = Map.of();
    private List<NaturalInfectionRule> rules = List.of();
    private boolean frozen;

    private NaturalInfectionManager() { super(new Gson(), "natural_infections"); }

    public synchronized void registerJava(NaturalStrainDefinition strain) {
        ensureOpen();
        if (javaStrains.putIfAbsent(strain.id(), strain) != null) {
            throw new IllegalArgumentException("Duplicate natural strain " + strain.id());
        }
    }

    public synchronized void registerJava(NaturalInfectionRule rule) {
        ensureOpen();
        if (javaRules.putIfAbsent(rule.id(), rule) != null) {
            throw new IllegalArgumentException("Duplicate natural infection rule " + rule.id());
        }
    }

    public synchronized void freezeJavaRegistrations() { frozen = true; }
    private void ensureOpen() {
        if (frozen) throw new IllegalStateException("Natural infection registry is frozen");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, NaturalStrainDefinition> loadedStrains = new LinkedHashMap<>();
        Map<ResourceLocation, NaturalInfectionRule> loadedRules = new LinkedHashMap<>();
        objects.forEach((fileId, element) -> {
            try {
                JsonObject root = element.getAsJsonObject();
                if (root.has("strains")) for (JsonElement entry : root.getAsJsonArray("strains")) {
                    NaturalStrainDefinition strain = parseStrain(entry.getAsJsonObject());
                    loadedStrains.put(strain.id(), strain);
                }
                if (root.has("rules")) for (JsonElement entry : root.getAsJsonArray("rules")) {
                    NaturalInfectionRule rule = parseRule(entry.getAsJsonObject());
                    loadedRules.put(rule.id(), rule);
                }
            } catch (RuntimeException exception) {
                BioForge.LOGGER.error("Failed to load natural infection file {}: {}",
                        fileId, exception.getMessage());
            }
        });
        javaStrains.forEach(loadedStrains::putIfAbsent);
        javaRules.forEach(loadedRules::putIfAbsent);
        strains = Map.copyOf(loadedStrains);
        rules = List.copyOf(loadedRules.values());
        BioForge.LOGGER.info("Loaded {} natural strains and {} host rules", strains.size(), rules.size());
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) { event.addListener(INSTANCE); }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof LivingEntity entity)
                || !BioForgeServerConfig.naturalInfectionsEnabled()
                || entity instanceof Player
                || entity.getPersistentData().getInt(CHECKED_TAG) >= CHECK_VERSION) return;
        level.getServer().execute(() -> INSTANCE.tryNaturalInfection(entity));
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity.level() instanceof ServerLevel)
                || entity instanceof Player
                || !BioForgeServerConfig.naturalInfectionsEnabled()
                || entity.tickCount % 100 != Math.floorMod(entity.getId(), 100)
                || entity.getPersistentData().getInt(CHECKED_TAG) >= CHECK_VERSION) return;
        INSTANCE.tryNaturalInfection(entity);
    }

    private void tryNaturalInfection(LivingEntity entity) {
        if (!entity.isAlive() || !(entity.level() instanceof ServerLevel)
                || rules.isEmpty()) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data == null) return;
        entity.getPersistentData().putInt(CHECKED_TAG, CHECK_VERSION);
        if (data.isInfected()) return;
        for (NaturalInfectionRule rule : rules) {
            if (!matches(entity, rule.entities()) || entity.getRandom().nextFloat() >= rule.chance()) continue;
            NaturalStrainDefinition definition = choose(rule, entity);
            if (definition != null) createStrain(definition, entity).applyToEntity(data, entity);
            return;
        }
    }

    private NaturalStrainDefinition choose(NaturalInfectionRule rule, LivingEntity entity) {
        int total = rule.strains().stream().mapToInt(NaturalInfectionRule.WeightedStrain::weight).sum();
        if (total <= 0) return null;
        int roll = entity.getRandom().nextInt(total);
        for (NaturalInfectionRule.WeightedStrain entry : rule.strains()) {
            roll -= entry.weight();
            if (roll < 0) return strains.get(entry.strain());
        }
        return null;
    }

    private static StrainData createStrain(NaturalStrainDefinition definition, LivingEntity entity) {
        StrainData strain = StrainData.createEmpty();
        strain.setColonyId(java.util.UUID.randomUUID());
        strain.setPathogenId(definition.pathogen());
        strain.setLifecycleProfileId(definition.lifecycleProfile());
        strain.getTransmissionIds().addAll(definition.transmissions());
        strain.getSymptoms().putAll(definition.symptoms());
        strain.addMutations(definition.mutations());
        for (NaturalStrainDefinition.RareMutation rare : definition.rareMutations()) {
            if (entity.getRandom().nextFloat() < rare.chance()) strain.addMutation(rare.mutation());
        }
        return strain;
    }

    private static boolean matches(LivingEntity entity, List<String> selectors) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        for (String selector : selectors) {
            if (selector.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(selector.substring(1));
                if (tagId != null && entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, tagId))) return true;
            } else if (entityId != null && entityId.equals(ResourceLocation.tryParse(selector))) return true;
        }
        return false;
    }

    private static NaturalStrainDefinition parseStrain(JsonObject json) {
        ResourceLocation id = requiredId(json, "id");
        ResourceLocation pathogen = requiredId(json, "pathogen");
        Set<ResourceLocation> transmissions = new LinkedHashSet<>();
        if (json.has("transmissions")) for (JsonElement value : json.getAsJsonArray("transmissions")) {
            ResourceLocation parsed = ResourceLocation.tryParse(value.getAsString());
            if (parsed != null) transmissions.add(parsed);
        }
        Map<String, String> symptoms = new LinkedHashMap<>();
        if (json.has("symptoms")) json.getAsJsonObject("symptoms").entrySet()
                .forEach(entry -> symptoms.put(entry.getKey(), entry.getValue().getAsString()));
        Set<String> mutations = strings(json, "mutations");
        List<NaturalStrainDefinition.RareMutation> rare = new ArrayList<>();
        if (json.has("rare_mutations")) for (JsonElement value : json.getAsJsonArray("rare_mutations")) {
            JsonObject entry = value.getAsJsonObject();
            rare.add(new NaturalStrainDefinition.RareMutation(entry.get("mutation").getAsString(),
                    entry.has("chance") ? entry.get("chance").getAsFloat() : 0.05F));
        }
        ResourceLocation lifecycle = json.has("lifecycle_profile")
                ? ResourceLocation.tryParse(json.get("lifecycle_profile").getAsString()) : null;
        return new NaturalStrainDefinition(id, pathogen, transmissions, symptoms,
                mutations, rare, lifecycle);
    }

    private static NaturalInfectionRule parseRule(JsonObject json) {
        ResourceLocation id = requiredId(json, "id");
        List<String> entities = List.copyOf(strings(json, "entities"));
        float chance = json.has("chance") ? json.get("chance").getAsFloat() : 0.05F;
        List<NaturalInfectionRule.WeightedStrain> strains = new ArrayList<>();
        for (JsonElement value : json.getAsJsonArray("strains")) {
            if (value.isJsonPrimitive()) {
                strains.add(new NaturalInfectionRule.WeightedStrain(
                        ResourceLocation.tryParse(value.getAsString()), 1));
            } else {
                JsonObject entry = value.getAsJsonObject();
                strains.add(new NaturalInfectionRule.WeightedStrain(
                        ResourceLocation.tryParse(entry.get("strain").getAsString()),
                        entry.has("weight") ? entry.get("weight").getAsInt() : 1));
            }
        }
        return new NaturalInfectionRule(id, entities, chance, strains);
    }

    private static ResourceLocation requiredId(JsonObject json, String key) {
        ResourceLocation id = json.has(key) ? ResourceLocation.tryParse(json.get(key).getAsString()) : null;
        if (id == null) throw new IllegalArgumentException("Missing or invalid " + key);
        return id;
    }

    private static Set<String> strings(JsonObject json, String key) {
        Set<String> result = new LinkedHashSet<>();
        if (!json.has(key)) return result;
        JsonArray array = json.getAsJsonArray(key);
        for (JsonElement value : array) result.add(value.getAsString());
        return result;
    }
}
