package net.jenkimods.bioforge.infection.naming;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public final class StrainNameStore extends SavedData {
    private static final String DATA_NAME = "bioforge_strain_names";
    private static final int VERSION = 2;
    private static final SavedData.Factory<StrainNameStore> FACTORY =
            new SavedData.Factory<>(StrainNameStore::new, StrainNameStore::load, null);
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public record Entry(String fingerprint, String name, @Nullable UUID researcherId,
                        String researcherName, long discoveredAt) {
        public boolean isNamed() {
            return name != null && !name.isBlank();
        }

        public Entry withResearcher(UUID id, String playerName) {
            return new Entry(fingerprint, name, id, playerName, discoveredAt);
        }

        public Entry withName(String replacement) {
            return new Entry(fingerprint, replacement, researcherId,
                    researcherName, discoveredAt);
        }
    }

    public record Discovery(Entry entry, boolean newlyDiscovered,
                            boolean researcherAssigned) {}

    public static StrainNameStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                FACTORY, DATA_NAME);
    }

    private static StrainNameStore load(CompoundTag root, HolderLookup.Provider registries) {
        StrainNameStore store = new StrainNameStore();
        int version = root.getInt("Version");
        CompoundTag records = root.getCompound("Records");
        for (String fingerprint : records.getAllKeys()) {
            CompoundTag tag = records.getCompound(fingerprint);
            UUID researcher = tag.hasUUID("Researcher")
                    ? tag.getUUID("Researcher") : null;
            String name = tag.getString("Name");
            String researcherName = tag.getString("ResearcherName");
            if (version < VERSION && name.isBlank()) {
                researcher = null;
                researcherName = "";
            }
            Entry entry = new Entry(fingerprint, name, researcher,
                    researcherName, tag.getLong("DiscoveredAt"));
            store.entries.put(fingerprint, entry);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("Version", VERSION);
        CompoundTag records = new CompoundTag();
        for (Entry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            if (entry.isNamed()) tag.putString("Name", entry.name());
            if (entry.researcherId() != null) {
                tag.putUUID("Researcher", entry.researcherId());
            }
            if (!entry.researcherName().isBlank()) {
                tag.putString("ResearcherName", entry.researcherName());
            }
            tag.putLong("DiscoveredAt", Math.max(0L, entry.discoveredAt()));
            records.put(entry.fingerprint(), tag);
        }
        root.put("Records", records);
        return root;
    }

    public Discovery discover(String fingerprint, @Nullable UUID researcherId,
                              String researcherName, long gameTime) {
        Entry existing = entries.get(fingerprint);
        if (existing == null) {
            Entry created = new Entry(fingerprint, "", researcherId,
                    researcherId == null ? "" : researcherName,
                    Math.max(0L, gameTime));
            entries.put(fingerprint, created);
            setDirty();
            return new Discovery(created, true, researcherId != null);
        }
        if (existing.researcherId() == null && researcherId != null
                && !existing.isNamed()) {
            Entry claimed = existing.withResearcher(researcherId, researcherName);
            entries.put(fingerprint, claimed);
            setDirty();
            return new Discovery(claimed, false, true);
        }
        return new Discovery(existing, false, false);
    }

    public Optional<Entry> find(String fingerprint) {
        return Optional.ofNullable(entries.get(fingerprint));
    }

    public boolean nameFirstDiscovery(String fingerprint, UUID researcherId,
                                      String name) {
        Entry entry = entries.get(fingerprint);
        if (entry == null || entry.isNamed()
                || !researcherId.equals(entry.researcherId())) return false;
        entries.put(fingerprint, entry.withName(name));
        setDirty();
        return true;
    }

    public boolean rename(String fingerprint, String name) {
        Entry entry = entries.get(fingerprint);
        if (entry == null) return false;
        entries.put(fingerprint, entry.withName(name));
        setDirty();
        return true;
    }

    public boolean isNameTaken(String name, @Nullable String exceptFingerprint) {
        return entries.values().stream().anyMatch(entry -> entry.isNamed()
                && !entry.fingerprint().equals(exceptFingerprint)
                && entry.name().equalsIgnoreCase(name));
    }

    public List<Entry> entries() {
        List<Entry> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparingLong(Entry::discoveredAt)
                .thenComparing(Entry::fingerprint));
        return List.copyOf(result);
    }

    public Map<String, String> namedEntries() {
        Map<String, String> result = new LinkedHashMap<>();
        entries().stream().filter(Entry::isNamed)
                .forEach(entry -> result.put(entry.fingerprint(), entry.name()));
        return Map.copyOf(result);
    }
}
