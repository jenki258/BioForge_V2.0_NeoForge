package net.jenkimods.bioforge.infection;

import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.jenkimods.bioforge.vaccine.StrainImmunityManager;
import net.jenkimods.bioforge.vaccine.VaccineManager;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleState;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

public class StrainData {
    private UUID colonyId;
    private PathogenType pathogen;
    private ResourceLocation pathogenId;
    private final Set<InfectionType> infectionTypes = EnumSet.noneOf(InfectionType.class);
    private final Set<ResourceLocation> transmissionIds = new LinkedHashSet<>();
    private final Map<String, String> symptoms = new LinkedHashMap<>();
    private final Set<String> mutationIds = new HashSet<>();
    private ResourceLocation lifecycleProfileId = InfectionLifecycleState.DEFAULT_PROFILE;

    private static Map<String, SymptomKey<?>> ALL_SYMPTOM_KEYS;

    private StrainData() {}

    private static Map<String, SymptomKey<?>> getAllSymptomKeys() {
        if (ALL_SYMPTOM_KEYS == null) {
            ALL_SYMPTOM_KEYS = BioForgeSymptoms.getAllSymptomKeys();
        }
        return ALL_SYMPTOM_KEYS;
    }

    public static StrainData createEmpty() {
        return new StrainData();
    }

    public static StrainData buildFrom(InfectionData data) {
        StrainData s = new StrainData();
        if (data.getPathogenId() != null) {
            s.colonyId = UUID.randomUUID();
            s.setPathogenId(data.getPathogenId());
            data.getInfectionTypes().stream()
                    .filter(BioForgeServerConfig::isTransmissionEnabled)
                    .forEach(s.infectionTypes::add);
            s.transmissionIds.addAll(data.getTransmissionIds());
            s.lifecycleProfileId = data.getLifecycle().profileId();

            for (Map.Entry<String, SymptomKey<?>> entry : getAllSymptomKeys().entrySet()) {
                String keyId = entry.getKey();
                if (!BioForgeServerConfig.isSymptomEnabled(keyId)) continue;
                SymptomKey<?> key = entry.getValue();
                Object value = data.getSymptom(key);
                if (value != null) {
                    s.symptoms.put(keyId, serializeSymptomValue(value));
                }
            }

            data.getSymptoms().getMutations().stream()
                    .filter(BioForgeServerConfig::isMutationEnabled)
                    .forEach(s.mutationIds::add);
        }
        return s;
    }

    public static StrainData parse(String payload) {
        StrainData s = new StrainData();
        if (payload == null || payload.equals("CLEAN") || payload.isEmpty()) return s;

        String[] parts = payload.split(";");
        if (parts.length == 0) return s;

        String[] header = parts[0].split("\\|");
        if (header.length >= 3) {
            try { s.colonyId = UUID.fromString(header[0]); } catch (Exception ignored) {}
            s.setPathogenId(parsePathogenId(header[1]));
            s.parseTypes(header[2]);
        } else if (header.length == 2) {
            s.setPathogenId(parsePathogenId(header[0]));
            s.parseTypes(header[1]);
        } else if (header.length == 1) {
            s.setPathogenId(parsePathogenId(header[0]));
        }

        Map<String, SymptomKey<?>> allKeys = getAllSymptomKeys();
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length == 2) {
                if (kv[0].equals("mutations")) {
                    s.parseMutations(kv[1]);
                } else if (kv[0].equals("lifecycle_profile")) {
                    ResourceLocation profile = ResourceLocation.tryParse(kv[1]);
                    if (profile != null) s.lifecycleProfileId = profile;
                } else if (allKeys.containsKey(kv[0])
                        && BioForgeServerConfig.isSymptomEnabled(kv[0])) {
                    s.symptoms.put(kv[0], kv[1]);
                }
            }
        }
        return s;
    }

    public Optional<UUID> getColonyId() { return Optional.ofNullable(colonyId); }
    public PathogenType getPathogen() { return pathogen; }
    public ResourceLocation getPathogenId() { return pathogenId; }
    public Set<InfectionType> getInfectionTypes() {
        syncLegacyTransmissions();
        return infectionTypes;
    }
    public Set<ResourceLocation> getTransmissionIds() {
        syncTransmissionIds();
        return transmissionIds;
    }
    public Map<String, String> getSymptoms() { return symptoms; }
    public Set<String> getMutationIds() { return mutationIds; }
    public ResourceLocation getLifecycleProfileId() { return lifecycleProfileId; }
    public Optional<String> getSymptom(String key) { return Optional.ofNullable(symptoms.get(key)); }

    public void setColonyId(UUID id) { this.colonyId = id; }
    public void setLifecycleProfileId(ResourceLocation id) {
        lifecycleProfileId = id == null ? InfectionLifecycleState.DEFAULT_PROFILE : id;
    }
    public void setPathogen(PathogenType pathogen) {
        this.pathogen = pathogen;
        this.pathogenId = pathogen == null ? null : BioForgeIds.pathogen(pathogen);
    }
    public void setPathogenId(ResourceLocation pathogenId) {
        this.pathogenId = pathogenId == null ? null
                : BioForgeDefinitionManager.PATHOGENS.canonicalId(pathogenId);
        PathogenType legacy = BioForgeIds.legacyPathogen(this.pathogenId);
        this.pathogen = this.pathogenId == null ? null
                : legacy == null ? PathogenType.UNIVERSAL : legacy;
        if (this.pathogenId != null
                && lifecycleProfileId.equals(InfectionLifecycleState.DEFAULT_PROFILE)) {
            lifecycleProfileId = InfectionLifecycleRegistry.INSTANCE
                    .profileForPathogen(this.pathogenId);
        }
    }

    public StrainData addMutation(String id) {
        if (BioForgeServerConfig.isMutationEnabled(id)) mutationIds.add(id);
        return this;
    }

    public StrainData addMutations(Collection<String> ids) {
        ids.stream().filter(BioForgeServerConfig::isMutationEnabled).forEach(mutationIds::add);
        return this;
    }






    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean applyMutationInVitro(MutationDefinition definition) {
        if (definition == null || !BioForgeServerConfig.isMutationEnabled(definition.id())
                || mutationIds.contains(definition.id())) return false;
        mutationIds.add(definition.id());
        for (MutationDefinition.Effect effect : definition.effects()) {
            if (effect.trigger() != MutationDefinition.Trigger.APPLY) continue;
            switch (effect.type()) {
                case "modify_symptom", "set_symptom" -> {
                    if (!BioForgeServerConfig.isSymptomEnabled(effect.target())) continue;
                    SymptomKey key = getAllSymptomKeys().get(effect.target());
                    if (key == null) continue;
                    String stored = symptoms.get(effect.target());
                    Object current = stored == null
                            ? key.getDefaultValue()
                            : parseSymptomValue(stored, key.getType());
                    if (current == null) current = key.getDefaultValue();
                    Object result = inVitroSymptomValue(current, key.getType(), effect);
                    if (result != null) {
                        symptoms.put(effect.target(), serializeSymptomValue(result));
                    }
                }
                case "add_infection_type" -> {
                    ResourceLocation id = parseTransmissionId(effect.target());
                    InfectionType type = BioForgeIds.legacyTransmission(id);
                    boolean enabled = type == null || BioForgeServerConfig.isTransmissionEnabled(type);
                    if (id != null && enabled && (pathogenId == null
                            || BioForgeDefinitionManager.allowsTransmission(pathogenId, id))) {
                        transmissionIds.add(id);
                        if (type != null) infectionTypes.add(type);
                    }
                }
                case "remove_infection_type" -> {
                    ResourceLocation id = parseTransmissionId(effect.target());
                    if (id != null) transmissionIds.remove(id);
                    InfectionType type = BioForgeIds.legacyTransmission(id);
                    if (type != null) infectionTypes.remove(type);
                }
                default -> {

                }
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object inVitroSymptomValue(Object current, Class<?> type,
                                               MutationDefinition.Effect effect) {
        String operation = effect.operation();
        if (type == Float.class || type == Integer.class) {
            if (!(current instanceof Number number)) return null;
            float value = effect.floatValue("value", 1.0f);
            float result = switch (operation) {
                case "add" -> number.floatValue() + value;
                case "multiply" -> number.floatValue() * value;
                case "min" -> Math.min(number.floatValue(), value);
                case "max" -> Math.max(number.floatValue(), value);
                case "clamp" -> number.floatValue();
                default -> value;
            };
            float minimum = effect.floatValue("min", -Float.MAX_VALUE);
            float maximum = effect.floatValue("max", Float.MAX_VALUE);
            if (minimum > maximum) {
                float swap = minimum;
                minimum = maximum;
                maximum = swap;
            }
            result = Math.max(minimum, Math.min(maximum, result));
            return type == Integer.class ? Math.round(result) : result;
        }
        if (type == Boolean.class) {
            boolean now = current instanceof Boolean bool && bool;
            boolean configured = effect.booleanValue("value",
                    effect.floatValue("value", 1.0f) > 0.5f);
            return switch (operation) {
                case "toggle" -> !now;
                case "and" -> now && configured;
                case "or" -> now || configured;
                default -> configured;
            };
        }
        if (type.isEnum()) {
            String value = effect.stringValue("value", "");
            if (value.isBlank()) return current;
            try {
                return Enum.valueOf((Class<Enum>) type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return current;
            }
        }
        return null;
    }

    public static String replaceColonyId(String payload, String newColonyId) {
        int firstPipe = payload.indexOf('|');
        if (firstPipe == -1) return payload;
        int secondPipe = payload.indexOf('|', firstPipe + 1);
        if (secondPipe == -1) return payload;
        return newColonyId + payload.substring(firstPipe);
    }

    public String toPayload() {
        StringBuilder sb = new StringBuilder();
        sb.append(colonyId != null ? colonyId.toString() : "PLACEHOLDER").append("|");
        sb.append(pathogenId != null ? BioForgeIds.legacyCompatible(pathogenId) : "UNKNOWN").append("|");
        Iterator<ResourceLocation> iter = getTransmissionIds().stream()
                .filter(StrainData::isTransmissionEnabled).iterator();
        while (iter.hasNext()) {
            sb.append(BioForgeIds.legacyCompatible(iter.next()));
            if (iter.hasNext()) sb.append(",");
        }
        sb.append(";");
        for (Map.Entry<String, String> entry : symptoms.entrySet()) {
            if (!BioForgeServerConfig.isSymptomEnabled(entry.getKey())) continue;
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }
        if (!mutationIds.isEmpty()) {
            sb.append("mutations=");
            StringJoiner joiner = new StringJoiner(",");
            for (String id : mutationIds) {
                if (BioForgeServerConfig.isMutationEnabled(id)) joiner.add(id);
            }
            sb.append(joiner);
            sb.append(";");
        }
        sb.append("lifecycle_profile=").append(lifecycleProfileId).append(";");
        return sb.toString();
    }





    public String toCanonicalGeneticPayload() {
        StringBuilder result = new StringBuilder();
        result.append(pathogenId != null ? pathogenId : "UNKNOWN").append('|');
        getTransmissionIds().stream().filter(StrainData::isTransmissionEnabled)
                .map(ResourceLocation::toString).sorted()
                .forEach(type -> result.append(type).append(','));
        result.append(';');
        symptoms.entrySet().stream()
                .filter(entry -> BioForgeServerConfig.isSymptomEnabled(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.append(entry.getKey()).append('=')
                        .append(entry.getValue()).append(';'));
        if (!mutationIds.isEmpty()) {
            result.append("mutations=");
            mutationIds.stream().filter(BioForgeServerConfig::isMutationEnabled)
                    .sorted().forEach(id -> result.append(id).append(','));
            result.append(';');
        }
        result.append("lifecycle_profile=").append(lifecycleProfileId).append(';');
        return result.toString();
    }

    public static String canonicalGeneticPayload(String payload) {
        return parse(payload).toCanonicalGeneticPayload();
    }

    @SuppressWarnings("unchecked")
    public boolean applyToEntity(InfectionData data, LivingEntity target) {
        if (data == null || pathogenId == null) return false;
        if (InfectionInvulnerability.isEnabled(target)) {
            if (target instanceof net.minecraft.server.level.ServerPlayer player) {
                InfectionInvulnerability.ensureCured(player);
            }
            return false;
        }
        if (StrainImmunityManager.blocks(target, data, this)) return false;
        if (data.isInfected()) {
            StrainData existing = buildFrom(data);
            if (existing.toCanonicalGeneticPayload().equals(toCanonicalGeneticPayload())) return false;
            float incomingStrength = getInfectionStrength(this);
            float establishedStrength = getInfectionStrength(existing)
                    * (data.isInfectionActive() ? 1.15F : 1.0F);
            float incomingChance = incomingStrength / Math.max(0.01F,
                    incomingStrength + establishedStrength);
            if (target.getRandom().nextFloat() >= incomingChance) return false;
            MutationManager.clearMutations(data, target);
            data.clearInfection();
        }
        data.getLifecycle().setProfileId(lifecycleProfileId);
        data.setInfected(true);
        data.setPathogenId(pathogenId);
        for (ResourceLocation type : getTransmissionIds()) {
            if (isTransmissionEnabled(type)) data.addTransmissionId(type);
        }
        Map<String, SymptomKey<?>> allKeys = getAllSymptomKeys();
        for (Map.Entry<String, String> entry : symptoms.entrySet()) {
            if (!BioForgeServerConfig.isSymptomEnabled(entry.getKey())) continue;
            SymptomKey<?> key = allKeys.get(entry.getKey());
            if (key != null) {
                Object value = parseSymptomValue(entry.getValue(), key.getType());
                if (value != null) {
                    data.getSymptoms().set((SymptomKey) key, value);
                }
            }
        }
        for (String id : mutationIds) {
            if (BioForgeServerConfig.isMutationEnabled(id)) data.getSymptoms().addMutation(id);
        }
        if (data.isInfectionActive()) {
            MutationManager.refreshContinuousEffects(data, target);
        }
        if (target instanceof ServerPlayer player) {
            InfectionStore.get(player.serverLevel()).clearInfection(player.getUUID());
        }
        VaccineManager.persistAndSync(target, data);
        return true;
    }

    public static StrainData compete(StrainData existing, StrainData incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;

        Random rand = new Random();
        float r1 = 0.8f + rand.nextFloat() * 0.4f;
        float r2 = 0.8f + rand.nextFloat() * 0.4f;

        float existingStr = getInfectionStrength(existing);
        float incomingStr = getInfectionStrength(incoming);
        float ns = incomingStr * r1;
        float os = existingStr * r2;

        StrainData result;
        if (ns > os * 1.5f) {
            result = incoming;
        } else if (ns > os * 1.0f) {
            StrainData blend = copy(incoming);
            float avg = Math.min(1.0f, (existingStr + incomingStr) / 2f);
            blend.getSymptoms().put("infection_strength", String.valueOf(avg));
            result = blend;
        } else if (ns > os * 0.7f) {
            StrainData boosted = copy(existing);
            float newStr = Math.min(1.0f, existingStr + 0.05f);
            boosted.getSymptoms().put("infection_strength", String.valueOf(newStr));
            result = boosted;
        } else {
            result = existing;
        }


        result.getMutationIds().addAll(existing.getMutationIds());
        result.getMutationIds().addAll(incoming.getMutationIds());

        return result;
    }

    private static float getInfectionStrength(StrainData strain) {
        return strain.getSymptom("infection_strength")
                .map(Float::parseFloat)
                .orElse(0.5f);
    }

    private static StrainData copy(StrainData original) {
        StrainData copy = createEmpty();
        copy.setColonyId(original.getColonyId().orElse(null));
        copy.setPathogenId(original.getPathogenId());
        copy.getInfectionTypes().addAll(original.getInfectionTypes());
        copy.getTransmissionIds().addAll(original.getTransmissionIds());
        copy.getSymptoms().putAll(original.getSymptoms());
        copy.getMutationIds().addAll(original.getMutationIds());
        copy.setLifecycleProfileId(original.getLifecycleProfileId());
        return copy;
    }

    private static String serializeSymptomValue(Object value) {
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof Boolean || value instanceof Number || value instanceof String) return value.toString();
        return "";
    }

    private static <T> T parseSymptomValue(String string, Class<T> type) {
        try {
            if (type.isEnum()) {
                return (T) Enum.valueOf((Class<Enum>) type, string);
            } else if (type == Boolean.class) {
                return (T) Boolean.valueOf(string);
            } else if (type == Float.class) {
                return (T) Float.valueOf(string);
            } else if (type == Integer.class) {
                return (T) Integer.valueOf(string);
            } else if (type == String.class) {
                return (T) string;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void parseTypes(String raw) {
        infectionTypes.clear();
        transmissionIds.clear();
        if (raw == null || raw.isEmpty()) return;
        for (String t : raw.split(",")) {
            ResourceLocation id = parseTransmissionId(t);
            if (id == null) continue;
            transmissionIds.add(BioForgeDefinitionManager.TRANSMISSIONS.canonicalId(id));
            InfectionType legacy = BioForgeIds.legacyTransmission(id);
            if (legacy != null && BioForgeServerConfig.isTransmissionEnabled(legacy)) infectionTypes.add(legacy);
        }
    }

    private static ResourceLocation parsePathogenId(String raw) {
        if (raw == null || raw.isBlank() || "UNKNOWN".equalsIgnoreCase(raw)) return null;
        return BioForgeIds.parse(raw);
    }

    private static ResourceLocation parseTransmissionId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return BioForgeIds.parse(raw); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean isTransmissionEnabled(ResourceLocation id) {
        InfectionType legacy = BioForgeIds.legacyTransmission(id);
        return BioForgeDefinitionManager.TRANSMISSIONS.get(id).isPresent()
                && (legacy == null || BioForgeServerConfig.isTransmissionEnabled(legacy));
    }

    private void syncTransmissionIds() {
        for (InfectionType type : infectionTypes) transmissionIds.add(BioForgeIds.transmission(type));
    }

    private void syncLegacyTransmissions() {
        for (ResourceLocation id : transmissionIds) {
            InfectionType legacy = BioForgeIds.legacyTransmission(id);
            if (legacy != null && BioForgeServerConfig.isTransmissionEnabled(legacy)) infectionTypes.add(legacy);
        }
    }

    private void parseMutations(String raw) {
        mutationIds.clear();
        if (raw == null || raw.isEmpty()) return;
        for (String id : raw.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty() && BioForgeServerConfig.isMutationEnabled(trimmed)) {
                mutationIds.add(trimmed);
            }
        }
    }
}
