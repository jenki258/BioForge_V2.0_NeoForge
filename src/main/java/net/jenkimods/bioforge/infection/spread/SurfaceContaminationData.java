package net.jenkimods.bioforge.infection.spread;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class SurfaceContaminationData extends SavedData {
    private static final String DATA_NAME = "bioforge_surface_contamination";
    public static final int ETHANOL_PROTECTION_RADIUS = 2;
    private static final SavedData.Factory<SurfaceContaminationData> FACTORY =
            new SavedData.Factory<>(SurfaceContaminationData::new,
                    SurfaceContaminationData::load, null);
    private final Long2ObjectOpenHashMap<Contamination> contaminated =
            new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<String> canonicalByPosition =
            new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<StrainData> strainByPosition =
            new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet ethanolCoated = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> positionsByChunk =
            new Long2ObjectOpenHashMap<>();
    private final TreeMap<Long, LongOpenHashSet> positionsByExpiry = new TreeMap<>();

    public static SurfaceContaminationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                FACTORY, DATA_NAME);
    }

    public static SurfaceContaminationData load(CompoundTag tag,
                                                HolderLookup.Provider registries) {
        SurfaceContaminationData data = new SurfaceContaminationData();
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag stored = entries.getCompound(index);
            String strain = stored.getString("Strain");
            if (strain.isBlank() || "CLEAN".equals(strain)) continue;
            long position = stored.getLong("Pos");
            Contamination contamination = new Contamination(
                    strain,
                    Math.max(0.0F, Math.min(1.0F, stored.getFloat("Strength"))),
                    stored.getLong("Expires"),
                    Math.max(0L, stored.getLong("Touched")));
            StrainData parsed = StrainData.parse(strain);
            data.putInternal(position, contamination,
                    parsed.toCanonicalGeneticPayload(), parsed);
        }
        for (long position : tag.getLongArray("EthanolCoated")) {
            data.ethanolCoated.add(position);
        }
        return data;
    }

    public void contaminate(BlockPos pos, StrainData strain, float strength,
                            int durationTicks, long gameTime) {
        if (strain == null || strain.getPathogenId() == null || durationTicks <= 0) return;
        if (isProtectedByEthanol(pos)) return;
        String payload = strain.toPayload();
        String canonical = strain.toCanonicalGeneticPayload();
        float safeStrength = Math.max(0.01F, Math.min(1.0F, strength));
        long expiresAt = gameTime + durationTicks;
        long key = pos.asLong();
        Contamination current = contaminated.get(key);
        if (current == null || current.expiresAt() <= gameTime) {
            if (current != null) removeInternal(key);
            putInternal(key, new Contamination(payload, safeStrength, expiresAt, gameTime),
                    canonical, strain);
        } else if (canonical.equals(canonicalByPosition.get(key))) {
            putInternal(key, new Contamination(payload,
                    Math.max(current.strength(), safeStrength),
                    Math.max(current.expiresAt(), expiresAt), gameTime), canonical, strain);
        } else if (safeStrength >= current.strength()) {
            StrainData winner = StrainData.compete(
                    StrainData.parse(current.strainPayload()), strain);
            putInternal(key, new Contamination(winner.toPayload(),
                    Math.max(current.strength(), safeStrength),
                    Math.max(current.expiresAt(), expiresAt), gameTime),
                    winner.toCanonicalGeneticPayload(), winner);
        }
        setDirty();
    }

    public boolean coatWithEthanol(BlockPos pos) {
        long key = pos.asLong();
        boolean added = ethanolCoated.add(key);
        boolean cleaned = clearEthanolHalo(pos);
        if (added || cleaned) setDirty();
        return added;
    }

    public boolean isProtectedByEthanol(BlockPos pos) {
        int radius = ETHANOL_PROTECTION_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (ethanolCoated.contains(pos.offset(x, y, z).asLong())) return true;
                }
            }
        }
        return false;
    }

    private boolean clearEthanolHalo(BlockPos center) {
        boolean cleaned = false;
        int radius = ETHANOL_PROTECTION_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    long key = center.offset(x, y, z).asLong();
                    if (contaminated.containsKey(key)) {
                        removeInternal(key);
                        cleaned = true;
                    }
                }
            }
        }
        return cleaned;
    }

    public boolean isEthanolCoated(BlockPos pos) {
        return ethanolCoated.contains(pos.asLong());
    }

    public void removeEthanolCoating(BlockPos pos) {
        if (ethanolCoated.remove(pos.asLong())) setDirty();
    }

    public Optional<Contamination> contaminationAt(BlockPos pos, long gameTime) {
        long key = pos.asLong();
        Contamination value = contaminated.get(key);
        if (value == null) return Optional.empty();
        if (value.expiresAt() <= gameTime || value.strength() <= 0.001F) {
            removeInternal(key);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public void weaken(BlockPos pos, float amount, long gameTime) {
        long key = pos.asLong();
        Contamination current = contaminated.get(key);
        if (current == null) return;
        if (current.expiresAt() <= gameTime || current.strength() <= 0.001F) {
            removeInternal(key);
            setDirty();
            return;
        }
        float next = current.strength() - Math.max(0.0F, amount);
        if (next <= 0.001F) removeInternal(key);
        else putInternal(key, new Contamination(current.strainPayload(), next,
                current.expiresAt(), current.lastTouched()), canonicalByPosition.get(key),
                strainByPosition.get(key));
        setDirty();
    }

    public int clean(BlockPos center, int radius, float amount, long gameTime) {
        int cleaned = 0;
        boolean changed = false;
        float safeAmount = Math.max(0.0F, amount);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    long key = pos.asLong();
                    Contamination current = contaminated.get(key);
                    if (current == null) continue;
                    if (current.expiresAt() <= gameTime || current.strength() <= 0.001F) {
                        removeInternal(key);
                        changed = true;
                        continue;
                    }
                    cleaned++;
                    if (safeAmount >= 1.0F || current.strength() <= safeAmount) {
                        removeInternal(key);
                    } else {
                        putInternal(key, new Contamination(current.strainPayload(),
                                current.strength() - safeAmount, current.expiresAt(),
                                current.lastTouched()), canonicalByPosition.get(key),
                                strainByPosition.get(key));
                    }
                    changed = true;
                }
            }
        }
        if (changed) setDirty();
        return cleaned;
    }

    public int cleanTransmission(BlockPos center, int radius, InfectionType type,
                                 long gameTime) {
        int safeRadius = Math.max(1, radius);
        long radiusSquared = (long) safeRadius * safeRadius;
        LongArrayList removals = new LongArrayList();
        int minChunkX = (center.getX() - safeRadius) >> 4;
        int maxChunkX = (center.getX() + safeRadius) >> 4;
        int minChunkZ = (center.getZ() - safeRadius) >> 4;
        int maxChunkZ = (center.getZ() + safeRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LongOpenHashSet positions = positionsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                if (positions == null) continue;
                LongIterator iterator = positions.iterator();
                while (iterator.hasNext()) {
                    long key = iterator.nextLong();
                    Contamination contamination = contaminated.get(key);
                    if (contamination == null || contamination.expiresAt() <= gameTime
                            || contamination.strength() <= 0.001F) {
                        removals.add(key);
                        continue;
                    }
                    long dx = BlockPos.getX(key) - center.getX();
                    long dy = BlockPos.getY(key) - center.getY();
                    long dz = BlockPos.getZ(key) - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    StrainData strain = strainByPosition.get(key);
                    if (strain == null) strain = StrainData.parse(contamination.strainPayload());
                    if (BioForgeDefinitionManager.hasTransmissionBehavior(strain, type)) {
                        removals.add(key);
                    }
                }
            }
        }
        for (long key : removals) removeInternal(key);
        if (!removals.isEmpty()) setDirty();
        return removals.size();
    }

    public int purgeExpired(long gameTime, int limit) {
        int removed = 0;
        int budget = Math.max(1, limit);
        while (removed < budget && !positionsByExpiry.isEmpty()) {
            Map.Entry<Long, LongOpenHashSet> bucket = positionsByExpiry.firstEntry();
            if (bucket.getKey() > gameTime) break;
            LongIterator positions = bucket.getValue().iterator();
            while (positions.hasNext() && removed < budget) {
                long key = positions.nextLong();
                positions.remove();
                Contamination value = contaminated.get(key);
                if (value != null && value.expiresAt() <= gameTime) {
                    removeWithoutExpiry(key);
                    removed++;
                }
            }
            if (bucket.getValue().isEmpty()) positionsByExpiry.pollFirstEntry();
        }
        if (removed > 0) setDirty();
        return removed;
    }

    public int size() {
        return contaminated.size();
    }

    public ScanResult scan(BlockPos center, int radius, long gameTime) {
        int count = 0;
        float maximum = 0.0F;
        List<ScanMarker> markers = new ArrayList<>();
        int safeRadius = Math.max(1, radius);
        long radiusSquared = (long) safeRadius * safeRadius;
        LongArrayList expired = new LongArrayList();
        int minChunkX = (center.getX() - safeRadius) >> 4;
        int maxChunkX = (center.getX() + safeRadius) >> 4;
        int minChunkZ = (center.getZ() - safeRadius) >> 4;
        int maxChunkZ = (center.getZ() + safeRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LongOpenHashSet positions = positionsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                if (positions == null) continue;
                LongIterator iterator = positions.iterator();
                while (iterator.hasNext()) {
                    long key = iterator.nextLong();
                    Contamination contamination = contaminated.get(key);
                    if (contamination == null || contamination.expiresAt() <= gameTime
                            || contamination.strength() <= 0.001F) {
                        expired.add(key);
                        continue;
                    }
                    long dx = BlockPos.getX(key) - center.getX();
                    long dy = BlockPos.getY(key) - center.getY();
                    long dz = BlockPos.getZ(key) - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    count++;
                    maximum = Math.max(maximum, contamination.strength());
                    markers.add(new ScanMarker(BlockPos.of(key), contamination.strength()));
                }
            }
        }
        for (long key : expired) removeInternal(key);
        if (!expired.isEmpty()) setDirty();
        markers.sort(Comparator.comparingDouble(ScanMarker::strength).reversed()
                .thenComparingDouble(marker -> marker.position().distSqr(center)));
        if (markers.size() > 96) markers = new ArrayList<>(markers.subList(0, 96));
        List<BlockPos> coatedMarkers = new ArrayList<>();
        LongIterator coatedIterator = ethanolCoated.iterator();
        while (coatedIterator.hasNext()) {
            long key = coatedIterator.nextLong();
            long dx = BlockPos.getX(key) - center.getX();
            long dy = BlockPos.getY(key) - center.getY();
            long dz = BlockPos.getZ(key) - center.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                coatedMarkers.add(BlockPos.of(key));
            }
        }
        coatedMarkers.sort(Comparator.comparingDouble(position -> position.distSqr(center)));
        if (coatedMarkers.size() > 96) {
            coatedMarkers = new ArrayList<>(coatedMarkers.subList(0, 96));
        }
        return new ScanResult(count, maximum, List.copyOf(markers),
                List.copyOf(coatedMarkers));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        contaminated.long2ObjectEntrySet().forEach(entry -> {
            long position = entry.getLongKey();
            Contamination value = entry.getValue();
            CompoundTag stored = new CompoundTag();
            stored.putLong("Pos", position);
            stored.putString("Strain", value.strainPayload());
            stored.putFloat("Strength", value.strength());
            stored.putLong("Expires", value.expiresAt());
            stored.putLong("Touched", value.lastTouched());
            entries.add(stored);
        });
        tag.put("Entries", entries);
        tag.putLongArray("EthanolCoated", ethanolCoated.toLongArray());
        return tag;
    }

    public StrainData strainAt(BlockPos position, Contamination contamination) {
        long key = position.asLong();
        StrainData cached = strainByPosition.get(key);
        if (cached != null) return cached;
        StrainData parsed = StrainData.parse(contamination.strainPayload());
        strainByPosition.put(key, parsed);
        return parsed;
    }

    private void putInternal(long position, Contamination value, String canonical,
                             StrainData parsedStrain) {
        Contamination previous = contaminated.put(position, value);
        if (previous == null) {
            long chunk = chunkKey(position);
            LongOpenHashSet positions = positionsByChunk.get(chunk);
            if (positions == null) {
                positions = new LongOpenHashSet();
                positionsByChunk.put(chunk, positions);
            }
            positions.add(position);
        } else if (previous.expiresAt() != value.expiresAt()) {
            unscheduleExpiry(position, previous.expiresAt());
        }
        canonicalByPosition.put(position, canonical);
        if (parsedStrain == null) strainByPosition.remove(position);
        else strainByPosition.put(position, parsedStrain);
        if (previous == null || previous.expiresAt() != value.expiresAt()) {
            positionsByExpiry.computeIfAbsent(value.expiresAt(), ignored -> new LongOpenHashSet())
                    .add(position);
        }
    }

    private void removeInternal(long position) {
        Contamination removed = contaminated.remove(position);
        if (removed == null) return;
        unscheduleExpiry(position, removed.expiresAt());
        removeWithoutContamination(position);
    }

    private void removeWithoutExpiry(long position) {
        if (contaminated.remove(position) == null) return;
        removeWithoutContamination(position);
    }

    private void removeWithoutContamination(long position) {
        canonicalByPosition.remove(position);
        strainByPosition.remove(position);
        long chunk = chunkKey(position);
        LongOpenHashSet positions = positionsByChunk.get(chunk);
        if (positions != null) {
            positions.remove(position);
            if (positions.isEmpty()) positionsByChunk.remove(chunk);
        }
    }

    private void unscheduleExpiry(long position, long expiresAt) {
        LongOpenHashSet positions = positionsByExpiry.get(expiresAt);
        if (positions == null) return;
        positions.remove(position);
        if (positions.isEmpty()) positionsByExpiry.remove(expiresAt);
    }

    private static long chunkKey(long position) {
        return ChunkPos.asLong(BlockPos.getX(position) >> 4, BlockPos.getZ(position) >> 4);
    }

    public record Contamination(String strainPayload, float strength,
                                long expiresAt, long lastTouched) {}
    public record ScanMarker(BlockPos position, float strength) {}
    public record ScanResult(int contaminatedSurfaces, float maximumStrength,
                             List<ScanMarker> markers, List<BlockPos> ethanolCoatedMarkers) {}
}
