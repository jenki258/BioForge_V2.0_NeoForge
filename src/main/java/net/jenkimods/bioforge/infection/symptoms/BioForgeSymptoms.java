package net.jenkimods.bioforge.infection.symptoms;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.api.definition.PathogenDefinition;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.*;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

public final class BioForgeSymptoms {

    private BioForgeSymptoms() {}

    public static final SymptomKey<HeartRate> HEART_RATE =
            SymptomKey.create("heart_rate", HeartRate.class, HeartRate.NORMAL);
    public static final SymptomKey<LungSound> LUNG_SOUND =
            SymptomKey.create("lung_sound", LungSound.class, LungSound.NORMAL);
    public static final SymptomKey<Boolean> TEMPERATURE_PLUS =
            SymptomKey.create("temperature_plus", Boolean.class, false);
    public static final SymptomKey<Boolean> TEMPERATURE_MINUS =
            SymptomKey.create("temperature_minus", Boolean.class, false);
    public static final SymptomKey<Float> OTOSCOPE_REDNESS =
            SymptomKey.create("otoscope_redness", Float.class, 0.0f);
    public static final SymptomKey<Float> OTOSCOPE_LESIONS =
            SymptomKey.create("otoscope_lesions", Float.class, 0.0f);
    public static final SymptomKey<Float> OTOSCOPE_SECRETION =
            SymptomKey.create("otoscope_secretion", Float.class, 0.0f);
    public static final SymptomKey<Float> OTOSCOPE_SWELLING =
            SymptomKey.create("otoscope_swelling", Float.class, 0.0f);
    public static final SymptomKey<Float> REFLEX_DELAY =
            SymptomKey.create("reflex_delay", Float.class, 0.0f);
    public static final SymptomKey<Float> REFLEX_STRENGTH =
            SymptomKey.create("reflex_strength", Float.class, 0.5f);
    public static final SymptomKey<Float> NEURAL_DAMAGE =
            SymptomKey.create("neural_damage", Float.class, 0.0f);
    public static final SymptomKey<Float> OXYGEN_SATURATION =
            SymptomKey.create("oxygen_saturation", Float.class, 0.95f);
    public static final SymptomKey<Float> PERFUSION_INDEX =
            SymptomKey.create("perfusion_index", Float.class, 0.7f);
    public static final SymptomKey<Float> INFECTION_STRENGTH =
            SymptomKey.create("infection_strength", Float.class, 0.5f);
    public static final SymptomKey<Float> COLONY_RADIUS =
            SymptomKey.create("colony_radius", Float.class, 20.0f);
    public static final SymptomKey<Float> MAX_INFESTED_BLOCKS =
            SymptomKey.create("max_infested_blocks", Float.class, 100.0f);
    public static final SymptomKey<MicroscopeVisibility> MICROSCOPE_VISIBILITY =
            SymptomKey.create("microscope_visibility", MicroscopeVisibility.class, MicroscopeVisibility.NONE);

    private static final Map<PathogenType, Map<SymptomKey<?>, float[]>> DEFAULT_RANGES = new EnumMap<>(PathogenType.class);

    static {
        Map<SymptomKey<?>, float[]> bacteria = new LinkedHashMap<>();
        bacteria.put(OTOSCOPE_REDNESS,   new float[]{0.7f, 1.0f});
        bacteria.put(OTOSCOPE_SECRETION, new float[]{0.6f, 1.0f});
        bacteria.put(OTOSCOPE_LESIONS,   new float[]{0.3f, 0.6f});
        bacteria.put(OTOSCOPE_SWELLING,  new float[]{0.2f, 0.4f});
        bacteria.put(OXYGEN_SATURATION,  new float[]{0.75f, 0.9f});
        bacteria.put(PERFUSION_INDEX,    new float[]{0.5f, 0.8f});
        bacteria.put(REFLEX_DELAY,       new float[]{0.15f, 0.35f});
        bacteria.put(REFLEX_STRENGTH,    new float[]{0.7f, 0.7f});
        bacteria.put(NEURAL_DAMAGE,      new float[]{0.1f, 0.1f});
        bacteria.put(INFECTION_STRENGTH, new float[]{0.4f, 0.7f});
        bacteria.put(COLONY_RADIUS,        new float[]{18.0f, 22.0f});
        bacteria.put(MAX_INFESTED_BLOCKS,  new float[]{70.0f, 90.0f});
        DEFAULT_RANGES.put(PathogenType.BACTERIA, bacteria);

        Map<SymptomKey<?>, float[]> fungi = new LinkedHashMap<>();
        fungi.put(OTOSCOPE_REDNESS,   new float[]{0.1f, 0.3f});
        fungi.put(OTOSCOPE_SECRETION, new float[]{0.3f, 0.6f});
        fungi.put(OTOSCOPE_LESIONS,   new float[]{0.7f, 1.0f});
        fungi.put(OTOSCOPE_SWELLING,  new float[]{0.0f, 0.1f});
        fungi.put(OXYGEN_SATURATION,  new float[]{0.85f, 0.95f});
        fungi.put(PERFUSION_INDEX,    new float[]{0.4f, 0.8f});
        fungi.put(REFLEX_DELAY,       new float[]{0.25f, 0.45f});
        fungi.put(REFLEX_STRENGTH,    new float[]{0.3f, 0.3f});
        fungi.put(NEURAL_DAMAGE,      new float[]{0.4f, 0.4f});
        fungi.put(INFECTION_STRENGTH, new float[]{0.5f, 0.9f});
        fungi.put(COLONY_RADIUS,        new float[]{22.0f, 28.0f});
        fungi.put(MAX_INFESTED_BLOCKS,  new float[]{140.0f, 160.0f});
        DEFAULT_RANGES.put(PathogenType.FUNGI, fungi);

        Map<SymptomKey<?>, float[]> virus = new LinkedHashMap<>();
        virus.put(OTOSCOPE_REDNESS,   new float[]{0.3f, 0.6f});
        virus.put(OTOSCOPE_SECRETION, new float[]{0.1f, 0.3f});
        virus.put(OTOSCOPE_LESIONS,   new float[]{0.6f, 1.0f});
        virus.put(OTOSCOPE_SWELLING,  new float[]{0.3f, 0.6f});
        virus.put(OXYGEN_SATURATION,  new float[]{0.8f, 0.95f});
        virus.put(PERFUSION_INDEX,    new float[]{0.3f, 0.6f});
        virus.put(REFLEX_DELAY,       new float[]{0.08f, 0.18f});
        virus.put(REFLEX_STRENGTH,    new float[]{0.9f, 0.9f});
        virus.put(NEURAL_DAMAGE,      new float[]{0.0f, 0.0f});
        virus.put(INFECTION_STRENGTH, new float[]{0.3f, 0.8f});
        virus.put(COLONY_RADIUS,        new float[]{25.0f, 35.0f});
        virus.put(MAX_INFESTED_BLOCKS,  new float[]{110.0f, 130.0f});
        DEFAULT_RANGES.put(PathogenType.VIRUS, virus);

        Map<SymptomKey<?>, float[]> prion = new LinkedHashMap<>();
        prion.put(OTOSCOPE_REDNESS,   new float[]{0.0f, 0.0f});
        prion.put(OTOSCOPE_SECRETION, new float[]{0.0f, 0.0f});
        prion.put(OTOSCOPE_LESIONS,   new float[]{0.0f, 0.0f});
        prion.put(OTOSCOPE_SWELLING,  new float[]{0.0f, 0.0f});
        prion.put(OXYGEN_SATURATION,  new float[]{0.95f, 1.0f});
        prion.put(PERFUSION_INDEX,    new float[]{0.1f, 0.3f});
        prion.put(REFLEX_DELAY,       new float[]{0.5f, 0.8f});
        prion.put(REFLEX_STRENGTH,    new float[]{0.1f, 0.1f});
        prion.put(NEURAL_DAMAGE,      new float[]{0.9f, 0.9f});
        prion.put(INFECTION_STRENGTH, new float[]{0.1f, 0.3f});
        prion.put(COLONY_RADIUS,        new float[]{18.0f, 22.0f});
        prion.put(MAX_INFESTED_BLOCKS,  new float[]{90.0f, 110.0f});
        DEFAULT_RANGES.put(PathogenType.PRION, prion);

        Map<SymptomKey<?>, float[]> parasite = new LinkedHashMap<>();
        parasite.put(OTOSCOPE_REDNESS,   new float[]{0.5f, 0.8f});
        parasite.put(OTOSCOPE_SECRETION, new float[]{0.2f, 0.5f});
        parasite.put(OTOSCOPE_LESIONS,   new float[]{0.4f, 0.7f});
        parasite.put(OTOSCOPE_SWELLING,  new float[]{0.1f, 0.3f});
        parasite.put(OXYGEN_SATURATION,  new float[]{0.6f, 0.8f});
        parasite.put(PERFUSION_INDEX,    new float[]{0.4f, 0.7f});
        parasite.put(REFLEX_DELAY,       new float[]{0.2f, 0.5f});
        parasite.put(REFLEX_STRENGTH,    new float[]{0.5f, 0.5f});
        parasite.put(NEURAL_DAMAGE,      new float[]{0.3f, 0.3f});
        parasite.put(INFECTION_STRENGTH, new float[]{0.6f, 0.9f});
        parasite.put(COLONY_RADIUS,        new float[]{20.0f, 25.0f});
        parasite.put(MAX_INFESTED_BLOCKS,  new float[]{80.0f, 100.0f});
        DEFAULT_RANGES.put(PathogenType.PARASITE, parasite);

        Map<SymptomKey<?>, float[]> universal = new LinkedHashMap<>();
        universal.put(OTOSCOPE_REDNESS,   new float[]{0.0f, 1.0f});
        universal.put(OTOSCOPE_SECRETION, new float[]{0.0f, 1.0f});
        universal.put(OTOSCOPE_LESIONS,   new float[]{0.0f, 1.0f});
        universal.put(OTOSCOPE_SWELLING,  new float[]{0.0f, 1.0f});
        universal.put(OXYGEN_SATURATION,  new float[]{0.7f, 1.0f});
        universal.put(PERFUSION_INDEX,    new float[]{0.0f, 1.0f});
        universal.put(REFLEX_DELAY,       new float[]{0.0f, 0.3f});
        universal.put(REFLEX_STRENGTH,    new float[]{0.0f, 1.0f});
        universal.put(NEURAL_DAMAGE,      new float[]{0.0f, 0.5f});
        universal.put(INFECTION_STRENGTH, new float[]{0.5f, 0.5f});
        universal.put(COLONY_RADIUS,        new float[]{20.0f, 25.0f});
        universal.put(MAX_INFESTED_BLOCKS,  new float[]{90.0f, 110.0f});
        DEFAULT_RANGES.put(PathogenType.UNIVERSAL, universal);
    }

    public static SymptomDeserializer deserializer() {
        return BioForgeSymptoms::resolveKey;
    }

    public static Map<String, SymptomKey<?>> getAllSymptomKeys() {
        Map<String, SymptomKey<?>> map = new LinkedHashMap<>();
        for (Field field : BioForgeSymptoms.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && SymptomKey.class.isAssignableFrom(field.getType())) {
                try {
                    SymptomKey<?> key = (SymptomKey<?>) field.get(null);
                    map.put(key.getId(), key);
                } catch (Exception ignored) {}
            }
        }
        BioForgeDefinitionManager.dynamicSymptomKeys().forEach(map::putIfAbsent);
        return map;
    }

    public static Map<String, SymptomKey<?>> getEnabledSymptomKeys() {
        Map<String, SymptomKey<?>> enabled = new LinkedHashMap<>();
        getAllSymptomKeys().forEach((id, key) -> {
            if (BioForgeServerConfig.isSymptomEnabled(id)) enabled.put(id, key);
        });
        return enabled;
    }

    @Nullable
    private static Object resolveKey(String keyId, CompoundTag tag) {
        if (!BioForgeServerConfig.isSymptomEnabled(keyId)) return null;
        SymptomKey<?> key = getAllSymptomKeys().get(keyId);
        if (key == null) return null;
        Class<?> type = key.getType();
        try {
            if (type.isEnum()) {
                @SuppressWarnings("unchecked")
                Class<Enum> enumClass = (Class<Enum>) type;
                String name = tag.getString(keyId);
                return safeEnum(enumClass, name, (Enum) key.getDefaultValue());
            } else if (type == Boolean.class) {
                return tag.getBoolean(keyId);
            } else if (type == Float.class) {
                return tag.getFloat(keyId);
            } else if (type == Integer.class) {
                return tag.getInt(keyId);
            } else if (type == String.class) {
                return tag.getString(keyId);
            }
        } catch (Exception ignored) {}
        return key.getDefaultValue();
    }

    @Nullable
    public static Object deserializeSymptom(String keyId, CompoundTag tag) {
        return resolveKey(keyId, tag);
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> cls, String name, E fallback) {
        try { return Enum.valueOf(cls, name); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    public static void applyDefaultSymptoms(InfectionData data) {
        if (applyDefinitionDefaults(data)) return;
        PathogenType pathogen = data.getPathogenType();
        if (pathogen == null) pathogen = PathogenType.UNIVERSAL;
        Random rand = new Random();

        Map<SymptomKey<?>, float[]> ranges = DEFAULT_RANGES.getOrDefault(pathogen, DEFAULT_RANGES.get(PathogenType.UNIVERSAL));
        for (Map.Entry<SymptomKey<?>, float[]> entry : ranges.entrySet()) {
            SymptomKey<?> key = entry.getKey();
            if (!BioForgeServerConfig.isSymptomEnabled(key.getId())) continue;
            float[] minMax = entry.getValue();
            float value = minMax[0] + rand.nextFloat() * (minMax[1] - minMax[0]);
            setSymptom(data, key, Math.min(1.0f, value));
        }

        setIfEnabled(data, HEART_RATE, HeartRate.TACHY);
        setIfEnabled(data, LUNG_SOUND, LungSound.CRACKLE);
        setIfEnabled(data, TEMPERATURE_PLUS, true);
        setIfEnabled(data, TEMPERATURE_MINUS, false);

        float radius = switch (pathogen) {
            case FUNGI -> 25.0f;
            case VIRUS -> 30.0f;
            default -> 20.0f;
        };
        float maxBlocks = switch (pathogen) {
            case FUNGI -> 150.0f;
            case VIRUS -> 120.0f;
            case BACTERIA -> 80.0f;
            default -> 100.0f;
        };

        setIfEnabled(data, COLONY_RADIUS, radius);
        setIfEnabled(data, MAX_INFESTED_BLOCKS, maxBlocks);

        MicroscopeVisibility visibility = switch (pathogen) {
            case VIRUS -> MicroscopeVisibility.LOW;
            case BACTERIA -> MicroscopeVisibility.MEDIUM;
            case FUNGI -> MicroscopeVisibility.HIGH;
            case PARASITE -> MicroscopeVisibility.VERY_LOW;
            case PRION -> MicroscopeVisibility.EXTREME;
            default -> MicroscopeVisibility.NONE;
        };
        setIfEnabled(data, MICROSCOPE_VISIBILITY, visibility);

        if (pathogen != null) {
            for (InfectionType t : pathogen.getAllowedTransmissions()) {
                if (BioForgeServerConfig.isTransmissionEnabled(t)) data.addInfectionType(t);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean applyDefinitionDefaults(InfectionData data) {
        if (data.getPathogenId() == null) return false;
        PathogenDefinition pathogen = BioForgeDefinitionManager.pathogen(data.getPathogenId()).orElse(null);
        if (pathogen == null || pathogen.defaultSymptoms().isEmpty()) return false;
        Random random = new Random();
        Map<String, SymptomKey<?>> keys = getAllSymptomKeys();
        for (Map.Entry<net.minecraft.resources.ResourceLocation, PathogenDefinition.DefaultSymptomValue> entry
                : pathogen.defaultSymptoms().entrySet()) {
            String storageId = BioForgeDefinitionManager.storageId(entry.getKey());
            if (!BioForgeServerConfig.isSymptomEnabled(storageId)) continue;
            SymptomKey key = keys.get(storageId);
            if (key == null) continue;
            Object value = definitionValue(key, entry.getValue(), random);
            if (value != null) data.getSymptoms().set(key, value);
        }
        for (net.minecraft.resources.ResourceLocation transmission : pathogen.allowedTransmissions()) {
            data.addTransmissionId(transmission);
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    private static Object definitionValue(SymptomKey key,
                                          PathogenDefinition.DefaultSymptomValue configured,
                                          Random random) {
        Class<?> type = key.getType();
        try {
            if (type == Float.class) {
                float min = configured.minimum().getAsFloat();
                float max = configured.maximum().getAsFloat();
                return min + random.nextFloat() * (max - min);
            }
            if (type == Integer.class) {
                int min = configured.minimum().getAsInt();
                int max = configured.maximum().getAsInt();
                return min >= max ? min : min + random.nextInt(max - min + 1);
            }
            if (type == Boolean.class) return configured.minimum().getAsBoolean();
            if (type == String.class) return configured.minimum().getAsString();
            if (type.isEnum()) {
                return Enum.valueOf((Class<Enum>) type,
                        configured.minimum().getAsString().toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException exception) {
            BioForge.LOGGER.warn("Invalid default symptom value for {}", key.getId());
        }
        return key.getDefaultValue();
    }

    private static <T> void setIfEnabled(InfectionData data, SymptomKey<T> key, T value) {
        if (BioForgeServerConfig.isSymptomEnabled(key.getId())) data.setSymptom(key, value);
    }

    @SuppressWarnings("unchecked")
    private static void setSymptom(InfectionData data, SymptomKey<?> key, float value) {
        if (key.getType() == Float.class) {
            data.getSymptoms().set((SymptomKey) key, value);
        } else if (key.getType() == Integer.class) {
            data.getSymptoms().set((SymptomKey) key, (int) value);
        }
    }

    public static Map<SymptomKey<?>, float[]> getDefaultRanges(PathogenType pathogen) {
        return DEFAULT_RANGES.getOrDefault(pathogen, DEFAULT_RANGES.get(PathogenType.UNIVERSAL));
    }

    public static Map<SymptomKey<?>, float[]> getDefaultRanges(
            net.minecraft.resources.ResourceLocation pathogenId) {
        PathogenType legacy = net.jenkimods.bioforge.api.definition.BioForgeIds
                .legacyPathogen(pathogenId);
        Map<SymptomKey<?>, float[]> ranges = new LinkedHashMap<>(
                getDefaultRanges(legacy == null ? PathogenType.UNIVERSAL : legacy));
        PathogenDefinition definition = BioForgeDefinitionManager.pathogen(pathogenId).orElse(null);
        if (definition == null) return Map.copyOf(ranges);
        Map<String, SymptomKey<?>> keys = getAllSymptomKeys();
        definition.defaultSymptoms().forEach((symptomId, configured) -> {
            try {
                if (!configured.minimum().isJsonPrimitive()
                        || !configured.minimum().getAsJsonPrimitive().isNumber()
                        || !configured.maximum().isJsonPrimitive()
                        || !configured.maximum().getAsJsonPrimitive().isNumber()) return;
                SymptomKey<?> key = keys.get(BioForgeDefinitionManager.storageId(symptomId));
                if (key != null) {
                    ranges.put(key, new float[]{configured.minimum().getAsFloat(),
                            configured.maximum().getAsFloat()});
                }
            } catch (RuntimeException ignored) {
            }
        });
        return Map.copyOf(ranges);
    }
}
