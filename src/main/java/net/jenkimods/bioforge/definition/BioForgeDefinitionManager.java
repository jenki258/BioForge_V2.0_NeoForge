package net.jenkimods.bioforge.definition;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.api.definition.DefinitionEntry;
import net.jenkimods.bioforge.api.definition.DefinitionSource;
import net.jenkimods.bioforge.api.definition.PathogenDefinition;
import net.jenkimods.bioforge.api.definition.ReloadableDefinitionRegistry;
import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.api.definition.TransmissionDefinition;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.MicroscopeVisibility;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.HeartRate;
import net.jenkimods.bioforge.infection.LungSound;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.infection.network.InfectionNetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = BioForge.MODID)
public final class BioForgeDefinitionManager extends SimpleJsonResourceReloadListener {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();
    public static final ReloadableDefinitionRegistry<PathogenDefinition> PATHOGENS =
            new ReloadableDefinitionRegistry<>("pathogen");
    public static final ReloadableDefinitionRegistry<TransmissionDefinition> TRANSMISSIONS =
            new ReloadableDefinitionRegistry<>("transmission");
    public static final ReloadableDefinitionRegistry<SymptomDefinition> SYMPTOMS =
            new ReloadableDefinitionRegistry<>("symptom");
    public static final BioForgeDefinitionManager INSTANCE = new BioForgeDefinitionManager();

    private static volatile List<String> lastDiagnostics = List.of();
    private static volatile boolean lastReloadSuccessful = true;

    private BioForgeDefinitionManager() {
        super(GSON, "bioforge_definitions");
        bootstrapFallbacks();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static Optional<PathogenDefinition> pathogen(ResourceLocation id) {
        return PATHOGENS.get(id);
    }

    public static Optional<TransmissionDefinition> transmission(ResourceLocation id) {
        return TRANSMISSIONS.get(id);
    }

    public static Optional<SymptomDefinition> symptom(ResourceLocation id) {
        return SYMPTOMS.get(id);
    }

    public static List<String> diagnostics() { return lastDiagnostics; }
    public static boolean lastReloadSuccessful() { return lastReloadSuccessful; }
    public static long generation() {
        return Math.max(PATHOGENS.snapshot().generation(),
                Math.max(TRANSMISSIONS.snapshot().generation(), SYMPTOMS.snapshot().generation()));
    }

    public static List<String> validateCurrent() {
        List<String> issues = new ArrayList<>(lastDiagnostics);
        for (TransmissionDefinition transmission : TRANSMISSIONS.values()) {
            for (ResourceLocation behavior : transmission.behaviors()) {
                if (BioForgeIds.legacyTransmission(behavior) == null
                        && BioForgeBehaviorRegistry.transmission(behavior).isEmpty()) {
                    issues.add(transmission.id() + " requires missing transmission behavior " + behavior);
                }
            }
        }
        for (SymptomDefinition symptom : SYMPTOMS.values()) {
            for (ResourceLocation behavior : symptom.behaviors()) {
                if (BioForgeBehaviorRegistry.symptom(behavior).isEmpty()) {
                    issues.add(symptom.id() + " requires missing symptom behavior " + behavior);
                }
            }
        }
        for (PathogenDefinition pathogen : PATHOGENS.values()) {
            for (ResourceLocation transmission : pathogen.allowedTransmissions()) {
                if (TRANSMISSIONS.get(transmission).isEmpty()) {
                    issues.add(pathogen.id() + " references missing transmission " + transmission);
                }
            }
            for (ResourceLocation symptom : pathogen.defaultSymptoms().keySet()) {
                Optional<SymptomDefinition> definition = SYMPTOMS.get(symptom);
                if (definition.isEmpty()) {
                    issues.add(pathogen.id() + " references missing symptom " + symptom);
                } else {
                    validateDefault(pathogen.id(), symptom, pathogen.defaultSymptoms().get(symptom),
                            definition.get(), issues);
                }
            }
        }
        return List.copyOf(issues);
    }

    public static boolean isKnownTransmission(ResourceLocation id) {
        return TRANSMISSIONS.get(id).isPresent();
    }

    public static boolean allowsTransmission(ResourceLocation pathogenId, ResourceLocation transmissionId) {
        if (pathogenId == null || transmissionId == null) return false;
        ResourceLocation canonicalTransmission = TRANSMISSIONS.canonicalId(transmissionId);
        return PATHOGENS.get(pathogenId)
                .map(definition -> definition.allowedTransmissions().contains(canonicalTransmission))
                .orElse(false);
    }

    public static boolean hasTransmissionBehavior(Collection<ResourceLocation> transmissionIds,
                                                  ResourceLocation behaviorId) {
        for (ResourceLocation id : transmissionIds) {
            TransmissionDefinition definition = TRANSMISSIONS.get(id).orElse(null);
            if (definition != null && definition.behaviors().contains(behaviorId)) return true;
        }
        return false;
    }

    public static boolean hasTransmissionBehavior(InfectionData data, InfectionType legacyBehavior) {
        return data != null && hasTransmissionBehavior(data.getTransmissionIds(),
                BioForgeIds.transmission(legacyBehavior));
    }

    public static boolean hasTransmissionBehavior(StrainData strain, InfectionType legacyBehavior) {
        return strain != null && hasTransmissionBehavior(strain.getTransmissionIds(),
                BioForgeIds.transmission(legacyBehavior));
    }

    public static Map<String, SymptomKey<?>> dynamicSymptomKeys() {
        Map<String, SymptomKey<?>> result = new LinkedHashMap<>();
        for (SymptomDefinition definition : SYMPTOMS.values()) {
            String storageId = storageId(definition.id());
            Object fallback = definition.javaDefaultValue();
            result.put(storageId, createKey(storageId, definition.javaType(), fallback));
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SymptomKey<?> createKey(String id, Class<?> type, Object value) {
        return SymptomKey.create(id, (Class) type, value);
    }

    public static String storageId(ResourceLocation id) {
        return BioForge.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    public static ResourceLocation symptomId(String storageId) {
        return BioForgeIds.parse(storageId);
    }

    public static void freezeJavaRegistrations() {
        PATHOGENS.freezeJava();
        TRANSMISSIONS.freezeJava();
        SYMPTOMS.freezeJava();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                         ProfilerFiller profiler) {
        Staging staging = new Staging();
        List<String> errors = new ArrayList<>();
        objects.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                parseResource(entry.getKey(), entry.getValue(), staging);
            } catch (RuntimeException exception) {
                errors.add(entry.getKey() + ": " + exception.getMessage());
            }
        });
        if (errors.isEmpty()) validate(staging, errors);
        if (!errors.isEmpty()) {
            lastReloadSuccessful = false;
            lastDiagnostics = List.copyOf(errors);
            errors.forEach(error -> BioForge.LOGGER.error("BioForge definition reload rejected: {}", error));
            return;
        }
        PATHOGENS.commitDatapack(staging.pathogens, staging.pathogenAliases, List.of());
        TRANSMISSIONS.commitDatapack(staging.transmissions, staging.transmissionAliases, List.of());
        SYMPTOMS.commitDatapack(staging.symptoms, staging.symptomAliases, List.of());
        lastReloadSuccessful = true;
        lastDiagnostics = List.of();
        BioForge.LOGGER.info("Loaded {} pathogen, {} transmission and {} symptom definitions",
                PATHOGENS.ids().size(), TRANSMISSIONS.ids().size(), SYMPTOMS.ids().size());
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers()
                    .forEach(InfectionNetworkHandler::sendDefinitionsIfChanged));
        }
    }

    private static void parseResource(ResourceLocation source, JsonElement element, Staging staging) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("root must be an object");
        JsonObject root = element.getAsJsonObject();
        int schema = integer(root, "schema_version", SCHEMA_VERSION);
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schema_version " + schema);
        }
        String path = source.getPath();
        Category category = path.startsWith("pathogens/") ? Category.PATHOGEN
                : path.startsWith("transmissions/") ? Category.TRANSMISSION
                : path.startsWith("symptoms/") ? Category.SYMPTOM : null;
        if (category == null) throw new IllegalArgumentException("expected pathogens/, transmissions/ or symptoms/");
        if (root.has("definitions")) {
            JsonArray definitions = array(root, "definitions");
            for (JsonElement definition : definitions) {
                if (!definition.isJsonObject()) throw new IllegalArgumentException("definition must be an object");
                parseDefinition(category, source, definition.getAsJsonObject(), null, staging);
            }
        } else {
            String defaultPath = path.substring(path.indexOf('/') + 1);
            if (defaultPath.endsWith(".json")) defaultPath = defaultPath.substring(0, defaultPath.length() - 5);
            parseDefinition(category, source, root,
                    ResourceLocation.tryBuild(source.getNamespace(), defaultPath), staging);
        }
    }

    private static void parseDefinition(Category category, ResourceLocation source, JsonObject json,
                                        ResourceLocation fallbackId, Staging staging) {
        ResourceLocation id = json.has("id") ? BioForgeIds.parse(json.get("id").getAsString()) : fallbackId;
        if (id == null) throw new IllegalArgumentException("definition in " + source + " requires id");
        int priority = integer(json, "priority", 0);
        boolean replace = bool(json, "replace", false);
        boolean enabled = bool(json, "enabled", true);
        DefinitionEntry<?> entry;
        if (!enabled) {
            entry = DefinitionEntry.disabled(priority, replace, DefinitionSource.DATAPACK);
        } else {
            entry = switch (category) {
                case PATHOGEN -> DefinitionEntry.enabled(parsePathogen(id, json), priority, replace, DefinitionSource.DATAPACK);
                case TRANSMISSION -> DefinitionEntry.enabled(parseTransmission(id, json), priority, replace, DefinitionSource.DATAPACK);
                case SYMPTOM -> DefinitionEntry.enabled(parseSymptom(id, json), priority, replace, DefinitionSource.DATAPACK);
            };
        }
        putUnique(category, id, entry, staging);
        if (json.has("aliases")) {
            for (JsonElement alias : array(json, "aliases")) {
                putAlias(category, BioForgeIds.parse(alias.getAsString()), id, staging);
            }
        }
    }

    private static PathogenDefinition parsePathogen(ResourceLocation id, JsonObject json) {
        PathogenDefinition.Builder builder = PathogenDefinition.builder(id)
                .translationKey(string(json, "translation_key", ""))
                .environmental(bool(json, "environmental", false))
                .color(color(json.get("color"), 0xFFFFFF));
        if (json.has("allowed_transmissions")) {
            for (JsonElement value : array(json, "allowed_transmissions")) {
                builder.transmission(BioForgeIds.parse(value.getAsString()));
            }
        }
        if (json.has("default_symptoms")) {
            JsonObject defaults = object(json, "default_symptoms");
            for (Map.Entry<String, JsonElement> value : defaults.entrySet()) {
                ResourceLocation symptomId = BioForgeIds.parse(value.getKey());
                if (value.getValue().isJsonObject()) {
                    JsonObject range = value.getValue().getAsJsonObject();
                    JsonElement min = range.has("min") ? range.get("min") : range.get("value");
                    JsonElement max = range.has("max") ? range.get("max") : min;
                    if (min == null) throw new IllegalArgumentException("default symptom " + value.getKey() + " has no value");
                    builder.defaultSymptom(symptomId, new PathogenDefinition.DefaultSymptomValue(min, max));
                } else {
                    builder.defaultSymptom(symptomId, PathogenDefinition.DefaultSymptomValue.fixed(value.getValue()));
                }
            }
        }
        return builder.build();
    }

    private static TransmissionDefinition parseTransmission(ResourceLocation id, JsonObject json) {
        TransmissionDefinition.Builder builder = TransmissionDefinition.builder(id)
                .translationKey(string(json, "translation_key", ""));
        if (json.has("behaviors")) {
            for (JsonElement value : array(json, "behaviors")) builder.behavior(BioForgeIds.parse(value.getAsString()));
        }
        return builder.build();
    }

    private static SymptomDefinition parseSymptom(ResourceLocation id, JsonObject json) {
        SymptomDefinition.ValueType type;
        try {
            type = SymptomDefinition.ValueType.valueOf(string(json, "value_type", "float").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown symptom value_type");
        }
        double defaultMin = type == SymptomDefinition.ValueType.FLOAT || type == SymptomDefinition.ValueType.INTEGER
                ? -Double.MAX_VALUE : 0.0;
        double defaultMax = type == SymptomDefinition.ValueType.FLOAT || type == SymptomDefinition.ValueType.INTEGER
                ? Double.MAX_VALUE : 1.0;
        SymptomDefinition.Builder builder = SymptomDefinition.builder(id, type)
                .translationKey(string(json, "translation_key", ""))
                .range(number(json, "min", defaultMin), number(json, "max", defaultMax));
        if (json.has("default")) builder.defaultValue(json.get("default"));
        if (json.has("allowed_values")) {
            List<String> values = new ArrayList<>();
            for (JsonElement value : array(json, "allowed_values")) values.add(value.getAsString());
            builder.allowedValues(values);
        }
        if (json.has("behaviors")) {
            for (JsonElement value : array(json, "behaviors")) builder.behavior(BioForgeIds.parse(value.getAsString()));
        }
        return builder.build();
    }

    private static void validate(Staging staging, List<String> errors) {
        Map<ResourceLocation, TransmissionDefinition> transmissions = TRANSMISSIONS.preview(staging.transmissions);
        Map<ResourceLocation, SymptomDefinition> symptoms = SYMPTOMS.preview(staging.symptoms);
        Map<ResourceLocation, PathogenDefinition> pathogens = PATHOGENS.preview(staging.pathogens);
        for (TransmissionDefinition transmission : transmissions.values()) {
            for (ResourceLocation behavior : transmission.behaviors()) {
                if (BioForgeIds.legacyTransmission(behavior) == null
                        && BioForgeBehaviorRegistry.transmission(behavior).isEmpty()) {
                    errors.add(transmission.id() + " requires missing transmission behavior " + behavior);
                }
            }
        }
        for (SymptomDefinition symptom : symptoms.values()) {
            for (ResourceLocation behavior : symptom.behaviors()) {
                if (BioForgeBehaviorRegistry.symptom(behavior).isEmpty()) {
                    errors.add(symptom.id() + " requires missing symptom behavior " + behavior);
                }
            }
        }
        for (PathogenDefinition pathogen : pathogens.values()) {
            for (ResourceLocation transmission : pathogen.allowedTransmissions()) {
                if (!transmissions.containsKey(transmission)) {
                    errors.add(pathogen.id() + " references unknown transmission " + transmission);
                }
            }
            for (ResourceLocation symptom : pathogen.defaultSymptoms().keySet()) {
                SymptomDefinition definition = symptoms.get(symptom);
                if (definition == null) {
                    errors.add(pathogen.id() + " references unknown symptom " + symptom);
                } else {
                    validateDefault(pathogen.id(), symptom, pathogen.defaultSymptoms().get(symptom),
                            definition, errors);
                }
            }
        }
        validateAliases(staging.pathogenAliases, pathogens.keySet(), "pathogen", errors);
        validateAliases(staging.transmissionAliases, transmissions.keySet(), "transmission", errors);
        validateAliases(staging.symptomAliases, symptoms.keySet(), "symptom", errors);
    }

    private static void validateAliases(Map<ResourceLocation, ResourceLocation> aliases,
                                        Set<ResourceLocation> targets, String kind, List<String> errors) {
        for (Map.Entry<ResourceLocation, ResourceLocation> alias : aliases.entrySet()) {
            ResourceLocation current = alias.getValue();
            Set<ResourceLocation> visited = new LinkedHashSet<>();
            while (aliases.containsKey(current) && visited.add(current)) current = aliases.get(current);
            if (!visited.add(current)) errors.add(kind + " alias cycle starts at " + alias.getKey());
            else if (!targets.contains(current)) errors.add(kind + " alias " + alias.getKey() + " targets missing " + current);
        }
    }

    private static void validateDefault(ResourceLocation pathogenId, ResourceLocation symptomId,
                                        PathogenDefinition.DefaultSymptomValue value,
                                        SymptomDefinition definition, List<String> errors) {
        try {
            JsonElement minimum = value.minimum();
            JsonElement maximum = value.maximum();
            switch (definition.valueType()) {
                case BOOLEAN -> {
                    if (!minimum.isJsonPrimitive() || !minimum.getAsJsonPrimitive().isBoolean()
                            || !maximum.isJsonPrimitive() || !maximum.getAsJsonPrimitive().isBoolean()) {
                        throw new IllegalArgumentException("must be boolean");
                    }
                }
                case INTEGER -> {
                    double min = finiteNumber(minimum);
                    double max = finiteNumber(maximum);
                    if (min != Math.rint(min) || max != Math.rint(max)) {
                        throw new IllegalArgumentException("must use whole numbers");
                    }
                    validateRange(min, max, definition);
                }
                case FLOAT -> validateRange(finiteNumber(minimum), finiteNumber(maximum), definition);
                case STRING -> {
                    if (!minimum.isJsonPrimitive() || !minimum.getAsJsonPrimitive().isString()
                            || !maximum.isJsonPrimitive() || !maximum.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("must be a string");
                    }
                }
                case ENUM -> {
                    String min = minimum.getAsString();
                    String max = maximum.getAsString();
                    if (!definition.allowedValues().contains(min) || !definition.allowedValues().contains(max)) {
                        throw new IllegalArgumentException("contains a value outside allowed_values");
                    }
                }
            }
        } catch (RuntimeException exception) {
            errors.add(pathogenId + " has invalid default for " + symptomId + ": " + exception.getMessage());
        }
    }

    private static double finiteNumber(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("must be numeric");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException("must be finite");
        return number;
    }

    private static void validateRange(double min, double max, SymptomDefinition definition) {
        if (min > max) throw new IllegalArgumentException("min cannot exceed max");
        if (min < definition.minimum() || max > definition.maximum()) {
            throw new IllegalArgumentException("is outside symptom range "
                    + definition.minimum() + ".." + definition.maximum());
        }
    }

    @SuppressWarnings("unchecked")
    private static void putUnique(Category category, ResourceLocation id, DefinitionEntry<?> entry, Staging staging) {
        Map<ResourceLocation, ?> map = switch (category) {
            case PATHOGEN -> staging.pathogens;
            case TRANSMISSION -> staging.transmissions;
            case SYMPTOM -> staging.symptoms;
        };
        if (map.containsKey(id)) throw new IllegalArgumentException("duplicate definition id " + id);
        switch (category) {
            case PATHOGEN -> staging.pathogens.put(id, (DefinitionEntry<PathogenDefinition>) entry);
            case TRANSMISSION -> staging.transmissions.put(id, (DefinitionEntry<TransmissionDefinition>) entry);
            case SYMPTOM -> staging.symptoms.put(id, (DefinitionEntry<SymptomDefinition>) entry);
        }
    }

    private static void putAlias(Category category, ResourceLocation alias, ResourceLocation target, Staging staging) {
        Map<ResourceLocation, ResourceLocation> map = switch (category) {
            case PATHOGEN -> staging.pathogenAliases;
            case TRANSMISSION -> staging.transmissionAliases;
            case SYMPTOM -> staging.symptomAliases;
        };
        if (map.putIfAbsent(alias, target) != null) throw new IllegalArgumentException("duplicate alias " + alias);
    }

    private static void bootstrapFallbacks() {
        for (InfectionType type : InfectionType.values()) {
            ResourceLocation id = BioForgeIds.transmission(type);
            TRANSMISSIONS.registerJava(id, TransmissionDefinition.builder(id)
                    .behavior(id).build(), -1000, false, DefinitionSource.BUILTIN);
        }
        for (PathogenType pathogen : PathogenType.values()) {
            ResourceLocation id = BioForgeIds.pathogen(pathogen);
            Set<ResourceLocation> transmissions = new LinkedHashSet<>();
            for (InfectionType type : pathogen.getAllowedTransmissions()) transmissions.add(BioForgeIds.transmission(type));
            PATHOGENS.registerJava(id, PathogenDefinition.builder(id)
                    .environmental(pathogen.isEnvironmental()).transmissions(transmissions).build(),
                    -1000, false, DefinitionSource.BUILTIN);
        }
        registerSymptom("heart_rate", SymptomDefinition.ValueType.ENUM, new JsonPrimitive(HeartRate.NORMAL.name()),
                List.of(HeartRate.NORMAL.name(), HeartRate.TACHY.name(), HeartRate.BRADY.name()), 0, 1);
        registerSymptom("lung_sound", SymptomDefinition.ValueType.ENUM, new JsonPrimitive(LungSound.NORMAL.name()),
                List.of(LungSound.NORMAL.name(), LungSound.CRACKLE.name()), 0, 1);
        registerSymptom("temperature_plus", SymptomDefinition.ValueType.BOOLEAN, new JsonPrimitive(false), List.of(), 0, 1);
        registerSymptom("temperature_minus", SymptomDefinition.ValueType.BOOLEAN, new JsonPrimitive(false), List.of(), 0, 1);
        registerFloat("otoscope_redness", 0.0F, 0, 1);
        registerFloat("otoscope_lesions", 0.0F, 0, 1);
        registerFloat("otoscope_secretion", 0.0F, 0, 1);
        registerFloat("otoscope_swelling", 0.0F, 0, 1);
        registerFloat("reflex_delay", 0.0F, 0, 10000);
        registerFloat("reflex_strength", 0.5F, 0, 1);
        registerFloat("neural_damage", 0.0F, 0, 1);
        registerFloat("oxygen_saturation", 0.95F, 0, 1);
        registerFloat("perfusion_index", 0.7F, 0, 1);
        registerFloat("infection_strength", 0.5F, 0, 10000);
        registerFloat("colony_radius", 20.0F, 0, 10000);
        registerFloat("max_infested_blocks", 100.0F, 0, 10000);
        registerSymptom("microscope_visibility", SymptomDefinition.ValueType.ENUM,
                new JsonPrimitive(MicroscopeVisibility.NONE.name()),
                java.util.Arrays.stream(MicroscopeVisibility.values()).map(Enum::name).toList(), 0, 1);
    }

    private static void registerFloat(String path, float fallback, double min, double max) {
        registerSymptom(path, SymptomDefinition.ValueType.FLOAT, new JsonPrimitive(fallback), List.of(), min, max);
    }

    private static void registerSymptom(String path, SymptomDefinition.ValueType type, JsonElement fallback,
                                        List<String> allowed, double min, double max) {
        ResourceLocation id = BioForgeIds.bioforge(path);
        SYMPTOMS.registerJava(id, SymptomDefinition.builder(id, type).defaultValue(fallback)
                .allowedValues(allowed).range(min, max).build(), -1000, false, DefinitionSource.BUILTIN);
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
    private static double number(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }
    private static JsonArray array(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        return json.getAsJsonArray(key);
    }
    private static JsonObject object(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return json.getAsJsonObject(key);
    }
    private static int color(JsonElement value, int fallback) {
        if (value == null) return fallback;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt() & 0xFFFFFF;
        String text = value.getAsString().trim();
        if (text.startsWith("#")) text = text.substring(1);
        try { return Integer.parseInt(text, 16) & 0xFFFFFF; }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("invalid color"); }
    }

    private enum Category { PATHOGEN, TRANSMISSION, SYMPTOM }

    private static final class Staging {
        private final Map<ResourceLocation, DefinitionEntry<PathogenDefinition>> pathogens = new LinkedHashMap<>();
        private final Map<ResourceLocation, DefinitionEntry<TransmissionDefinition>> transmissions = new LinkedHashMap<>();
        private final Map<ResourceLocation, DefinitionEntry<SymptomDefinition>> symptoms = new LinkedHashMap<>();
        private final Map<ResourceLocation, ResourceLocation> pathogenAliases = new LinkedHashMap<>();
        private final Map<ResourceLocation, ResourceLocation> transmissionAliases = new LinkedHashMap<>();
        private final Map<ResourceLocation, ResourceLocation> symptomAliases = new LinkedHashMap<>();
    }
}
