package net.jenkimods.bioforge.infection.symptoms;

import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EntitySymptoms {

    private final Map<String, Object> values = new HashMap<>();
    private final Set<String> mutations = new HashSet<>();

    public <T> T get(SymptomKey<T> key) {
        if (!BioForgeServerConfig.isSymptomEnabled(key.getId())) return key.getDefaultValue();
        Object val = values.get(key.getId());
        if (val == null) return key.getDefaultValue();
        if (key.getType().isInstance(val)) return key.getType().cast(val);
        return key.getDefaultValue();
    }

    public <T> void set(SymptomKey<T> key, T value) {
        if (!BioForgeServerConfig.isSymptomEnabled(key.getId())) {
            values.remove(key.getId());
            return;
        }
        if (value == null) {
            values.remove(key.getId());
        } else {
            values.put(key.getId(), value);
        }
    }

    public <T> void clear(SymptomKey<T> key) {
        values.remove(key.getId());
    }

    public void clearAll() {
        values.clear();
        mutations.clear();
    }


    public Set<String> getMutations() {
        mutations.removeIf(id -> !BioForgeServerConfig.isMutationEnabled(id));
        return mutations;
    }
    public void addMutation(String id) {
        if (BioForgeServerConfig.isMutationEnabled(id)) mutations.add(id);
    }
    public void removeMutation(String id) { mutations.remove(id); }
    public boolean hasMutation(String id) {
        return BioForgeServerConfig.isMutationEnabled(id) && mutations.contains(id);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!BioForgeServerConfig.isSymptomEnabled(entry.getKey())) continue;
            Object val = entry.getValue();
            if (val instanceof Boolean b) tag.putBoolean(entry.getKey(), b);
            else if (val instanceof Integer i) tag.putInt(entry.getKey(), i);
            else if (val instanceof Float f) tag.putFloat(entry.getKey(), f);
            else if (val instanceof String s) tag.putString(entry.getKey(), s);
            else if (val instanceof Enum<?> e) tag.putString(entry.getKey(), e.name());
        }
        if (!mutations.isEmpty()) {
            tag.putString("Mutations", String.join(",", mutations));
        }
        return tag;
    }

    public void deserializeNBT(@NotNull CompoundTag tag, SymptomDeserializer deserializer) {
        values.clear();
        for (String key : tag.getAllKeys()) {
            if (key.equals("Mutations")) continue;
            Object resolved = deserializer.resolve(key, tag);
            if (resolved != null) values.put(key, resolved);
        }
        mutations.clear();
        if (tag.contains("Mutations")) {
            String raw = tag.getString("Mutations");
            if (!raw.isEmpty()) {
                for (String id : raw.split(",")) {
                    if (!id.isEmpty() && BioForgeServerConfig.isMutationEnabled(id)) {
                        mutations.add(id);
                    }
                }
            }
        }
    }
}
