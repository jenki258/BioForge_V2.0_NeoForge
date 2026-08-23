package net.jenkimods.bioforge.blood.knowledge;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.world.data.ReagentType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

public class BloodKnowledgeStore extends SavedData {

    private static final String DATA_NAME   = "bioforge_blood_knowledge";
    private static final int    MAX_ENTRIES = 2000;
    private static final SavedData.Factory<BloodKnowledgeStore> FACTORY =
            new SavedData.Factory<>(BloodKnowledgeStore::new,
                    BloodKnowledgeStore::load, null);

    private final Map<UUID, LinkedHashMap<UUID, BloodKnowledge>> data = new HashMap<>();
    private final Map<UUID, String> observedBloodTypes = new HashMap<>();

    public static BloodKnowledgeStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(FACTORY, DATA_NAME);
    }

    public BloodKnowledgeStore() {}

    public BloodKnowledge getOrCreate(UUID playerUUID, UUID subjectUUID,
                                      String subjectName, String subjectType, boolean isSubjectPlayer) {
        LinkedHashMap<UUID, BloodKnowledge> playerMap =
                data.computeIfAbsent(playerUUID, k -> new LinkedHashMap<>());
        return playerMap.computeIfAbsent(subjectUUID,
                k -> new BloodKnowledge(subjectUUID, subjectName, subjectType, isSubjectPlayer));
    }

    public Optional<BloodKnowledge> find(UUID playerUUID, UUID subjectUUID) {
        LinkedHashMap<UUID, BloodKnowledge> playerMap = data.get(playerUUID);
        if (playerMap == null) return Optional.empty();
        return Optional.ofNullable(playerMap.get(subjectUUID));
    }

    public Collection<BloodKnowledge> getAllForPlayer(UUID playerUUID) {
        LinkedHashMap<UUID, BloodKnowledge> playerMap = data.get(playerUUID);
        if (playerMap == null) return List.of();
        return List.copyOf(playerMap.values());
    }

    public int clearAllForPlayer(UUID playerUUID) {
        LinkedHashMap<UUID, BloodKnowledge> removed = data.remove(playerUUID);
        int count = removed != null ? removed.size() : 0;
        if (removed != null) {
            setDirty();
        }
        return count;
    }

    public boolean removeForPlayer(UUID playerUUID, UUID subjectUUID) {
        LinkedHashMap<UUID, BloodKnowledge> playerMap = data.get(playerUUID);
        if (playerMap == null) return false;

        BloodKnowledge removed = playerMap.remove(subjectUUID);
        if (removed == null) return false;

        if (playerMap.isEmpty()) {
            data.remove(playerUUID);
        }
        setDirty();
        return true;
    }

    public List<String> getSubjectNamesForPlayer(UUID playerUUID) {
        LinkedHashMap<UUID, BloodKnowledge> playerMap = data.get(playerUUID);
        if (playerMap == null) return List.of();

        return playerMap.values().stream()
                .map(BloodKnowledge::getSubjectName)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public int removeBySubjectNameForPlayer(UUID playerUUID, String subjectName) {
        LinkedHashMap<UUID, BloodKnowledge> playerMap = data.get(playerUUID);
        if (playerMap == null) return 0;

        int before = playerMap.size();
        playerMap.entrySet().removeIf(e -> e.getValue().getSubjectName().equalsIgnoreCase(subjectName));
        int removed = before - playerMap.size();

        if (removed > 0) {
            if (playerMap.isEmpty()) data.remove(playerUUID);
            setDirty();
        }

        return removed;
    }

    public void recordReagent(UUID playerUUID, UUID subjectUUID,
                              String subjectName, String subjectType, boolean isSubjectPlayer,
                              BloodType bloodType, ReagentType reagentType, boolean reacted) {
        validateSubjectBloodType(subjectUUID, bloodType);
        observedBloodTypes.put(subjectUUID, bloodType.name());
        BloodKnowledge k = getOrCreate(
                playerUUID, subjectUUID, subjectName, subjectType, isSubjectPlayer);

        switch (reagentType) {
            case ANTI_A -> k.setAntiA(reacted);
            case ANTI_B -> k.setAntiB(reacted);
            case ANTI_D -> k.setAntiD(reacted);
        }

        enforceLimit(playerUUID);
        setDirty();
    }

    public int validateSubjectBloodType(UUID subjectUUID, BloodType currentType) {
        String previous = observedBloodTypes.get(subjectUUID);
        if (previous == null) {
            boolean staleLegacyKnowledge = data.values().stream()
                    .map(knowledge -> knowledge.get(subjectUUID))
                    .filter(Objects::nonNull)
                    .anyMatch(knowledge -> !matchesRecordedReactions(
                            knowledge, currentType));
            if (staleLegacyKnowledge) return invalidateSubject(subjectUUID, currentType);
            observedBloodTypes.put(subjectUUID, currentType.name());
            setDirty();
            return 0;
        }
        if (previous.equals(currentType.name())) return 0;
        return invalidateSubject(subjectUUID, currentType);
    }

    private static boolean matchesRecordedReactions(BloodKnowledge knowledge,
                                                     BloodType currentType) {
        if (knowledge.getAntiA() != null && knowledge.getAntiA()
                != reactsToAntiA(currentType)) return false;
        if (knowledge.getAntiB() != null && knowledge.getAntiB()
                != reactsToAntiB(currentType)) return false;
        return knowledge.getAntiD() == null || knowledge.getAntiD()
                == reactsToAntiD(currentType);
    }

    private static boolean reactsToAntiA(BloodType type) {
        return type == BloodType.A_POSITIVE || type == BloodType.A_NEGATIVE
                || type == BloodType.AB_POSITIVE || type == BloodType.AB_NEGATIVE;
    }

    private static boolean reactsToAntiB(BloodType type) {
        return type == BloodType.B_POSITIVE || type == BloodType.B_NEGATIVE
                || type == BloodType.AB_POSITIVE || type == BloodType.AB_NEGATIVE;
    }

    private static boolean reactsToAntiD(BloodType type) {
        return type.isRhPositive();
    }

    public int invalidateSubject(UUID subjectUUID, BloodType currentType) {
        int removed = 0;
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, BloodKnowledge>>> players =
                data.entrySet().iterator();
        while (players.hasNext()) {
            LinkedHashMap<UUID, BloodKnowledge> knowledge = players.next().getValue();
            if (knowledge.remove(subjectUUID) != null) removed++;
            if (knowledge.isEmpty()) players.remove();
        }
        observedBloodTypes.put(subjectUUID, currentType.name());
        setDirty();
        return removed;
    }

    private void enforceLimit(UUID playerUUID) {
        LinkedHashMap<UUID, BloodKnowledge> playerMap = data.get(playerUUID);
        if (playerMap == null) return;

        while (playerMap.size() > MAX_ENTRIES) {
            UUID oldest = playerMap.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().getLastUpdated()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest != null) playerMap.remove(oldest);
        }
    }

    @Override
    public CompoundTag save(CompoundTag out, HolderLookup.Provider registries) {
        ListTag playersList = new ListTag();
        data.forEach((playerUUID, knowledgeMap) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("PlayerUUID", playerUUID);
            ListTag entriesList = new ListTag();
            knowledgeMap.values().forEach(k -> entriesList.add(k.serialize()));
            playerTag.put("Entries", entriesList);
            playersList.add(playerTag);
        });
        out.put("Players", playersList);
        ListTag observedList = new ListTag();
        observedBloodTypes.forEach((subjectUUID, bloodType) -> {
            CompoundTag observedTag = new CompoundTag();
            observedTag.putUUID("SubjectUUID", subjectUUID);
            observedTag.putString("BloodType", bloodType);
            observedList.add(observedTag);
        });
        out.put("ObservedBloodTypes", observedList);
        return out;
    }

    public static BloodKnowledgeStore load(CompoundTag tag,
                                           HolderLookup.Provider registries) {
        BloodKnowledgeStore store = new BloodKnowledgeStore();
        ListTag playersList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playersList.size(); i++) {
            CompoundTag playerTag = playersList.getCompound(i);
            UUID playerUUID = playerTag.getUUID("PlayerUUID");
            LinkedHashMap<UUID, BloodKnowledge> knowledgeMap = new LinkedHashMap<>();
            ListTag entriesList = playerTag.getList("Entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entriesList.size(); j++) {
                BloodKnowledge k = BloodKnowledge.deserialize(entriesList.getCompound(j));
                knowledgeMap.put(k.getSubjectUUID(), k);
            }
            store.data.put(playerUUID, knowledgeMap);
        }
        ListTag observedList = tag.getList("ObservedBloodTypes", Tag.TAG_COMPOUND);
        for (int index = 0; index < observedList.size(); index++) {
            CompoundTag observedTag = observedList.getCompound(index);
            if (observedTag.hasUUID("SubjectUUID")) {
                store.observedBloodTypes.put(observedTag.getUUID("SubjectUUID"),
                        observedTag.getString("BloodType"));
            }
        }
        return store;
    }
}
