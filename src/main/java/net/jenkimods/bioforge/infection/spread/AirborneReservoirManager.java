package net.jenkimods.bioforge.infection.spread;

import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.block.AirVentBlock;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class AirborneReservoirManager {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();

    private AirborneReservoirManager() {}

    public static void emit(ServerLevel level, AirRoomScanner.Room room,
                            StrainData strain, float amount, BlockPos source) {
        if (amount <= 0.0F || strain == null || strain.getPathogenId() == null
                || !BioForgeServerConfig.isTransmissionEnabled(InfectionType.AIR_BORNE)
                || AirVentBlock.isProtected(level, source)) return;
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState());
        String canonical = strain.toCanonicalGeneticPayload();
        ReservoirKey key = new ReservoirKey(room.signature(), canonical);
        long now = level.getGameTime();
        Reservoir current = state.reservoirs.get(key);
        float existing = current == null ? 0.0F : decayedConcentration(current, now);
        float dilution = room.outdoors() ? BioForgeServerConfig.outdoorAirMultiplier() : 1.0F;
        float next = Math.min(1.0F, existing + Math.max(0.0F, amount) * 0.32F * dilution);
        if (current == null) state.processingOrder.addLast(key);
        state.reservoirs.put(key, new Reservoir(strain, canonical, room, next, now, now,
                current == null ? now - 20L : current.lastProcessed(),
                current == null ? now : current.lastEnvironmentalDeposit()));
    }

    public static void tick(ServerLevel level) {
        if (!BioForgeServerConfig.isTransmissionEnabled(InfectionType.AIR_BORNE)) {
            LEVELS.remove(level);
            return;
        }
        LevelState state = LEVELS.get(level);
        if (state == null || state.reservoirs.isEmpty()) return;
        long now = level.getGameTime();
        if (state.lastProcessedTick == now) return;
        state.lastProcessedTick = now;
        long lifetime = BioForgeServerConfig.airborneReservoirLifetimeTicks();
        state.roomTargets.clear();
        int budget = Math.min(state.processingOrder.size(),
                BioForgeServerConfig.airborneWorkBudget());
        for (int processed = 0; processed < budget; processed++) {
            ReservoirKey key = state.processingOrder.pollFirst();
            if (key == null) break;
            Reservoir reservoir = state.reservoirs.get(key);
            if (reservoir == null) continue;
            float concentration = decayedConcentration(reservoir, now);
            if (concentration < 0.005F || now - reservoir.lastEmission() >= lifetime) {
                state.reservoirs.remove(key);
                continue;
            }
            float elapsedSeconds = Math.max(0.05F,
                    Math.min(10.0F, (now - reservoir.lastProcessed()) / 20.0F));
            Reservoir updated = reservoir.withProcessedConcentration(concentration, now);
            exposeRoom(level, state, updated, now, elapsedSeconds);
            if (now - reservoir.lastEnvironmentalDeposit() >= 40L) {
                depositEnvironmentalReservoir(level, updated, now);
                updated = updated.withEnvironmentalDeposit(now);
            }
            state.reservoirs.put(key, updated);
            state.processingOrder.addLast(key);
        }
        if (now % 200L == 0L) {
            state.exposures.values().removeIf(exposure -> now - exposure.lastExposure() > 200L);
        }
        if (state.reservoirs.isEmpty()) {
            state.processingOrder.clear();
            state.exposures.clear();
            state.roomTargets.clear();
            LEVELS.remove(level);
        }
    }

    public static void reduce(ServerLevel level, BlockPos center, int radius, float amount) {
        LevelState state = LEVELS.get(level);
        if (state == null) return;
        long now = level.getGameTime();
        state.reservoirs.replaceAll((key, reservoir) -> intersects(
                reservoir.room(), center, radius)
                ? reservoir.withConcentration(
                Math.max(0.0F, decayedConcentration(reservoir, now) - amount), now)
                : reservoir);
        state.reservoirs.entrySet().removeIf(entry -> entry.getValue().concentration() < 0.005F);
        if (state.reservoirs.isEmpty()) LEVELS.remove(level);
    }

    public static void clear(ServerLevel level) {
        LEVELS.remove(level);
        AirRoomScanner.invalidate(level);
    }

    public static ScanResult scan(ServerLevel level, BlockPos center) {
        if (AirVentBlock.isProtected(level, center)) {
            return new ScanResult(0, 0.0F, false, List.of());
        }
        LevelState state = LEVELS.get(level);
        AirRoomScanner.Room room = AirRoomScanner.scan(level, center);
        if (state == null) return new ScanResult(0, 0.0F, room.outdoors(), List.of());
        long now = level.getGameTime();
        long lifetime = BioForgeServerConfig.airborneReservoirLifetimeTicks();
        int count = 0;
        float maximum = 0.0F;
        AirRoomScanner.Room strongestRoom = null;
        for (Reservoir reservoir : state.reservoirs.values()) {
            if (now - reservoir.lastEmission() >= lifetime) continue;
            if (reservoir.room().signature() != room.signature()
                    && !reservoir.room().contains(center)) continue;
            float concentration = decayedConcentration(reservoir, now);
            if (concentration < 0.005F) continue;
            count++;
            if (concentration > maximum) {
                maximum = concentration;
                strongestRoom = reservoir.room();
            }
        }
        return new ScanResult(count, maximum, room.outdoors(),
                visualizationCells(strongestRoom, center, 80));
    }

    private static void exposeRoom(ServerLevel level, LevelState levelState,
                                   Reservoir reservoir, long now, float elapsedSeconds) {
        StrainData strain = reservoir.strain();
        if (strain.getPathogenId() == null) return;
        float strength = strain.getSymptom("infection_strength")
                .flatMap(AirborneReservoirManager::parseFloat)
                .orElse(0.5F);
        float incrementBase = BioForgeServerConfig.airExposureChance()
                * reservoir.concentration() * (0.5F + Math.max(0.0F, strength))
                * elapsedSeconds * InfectionLifecycleRegistry.INSTANCE.infectivity(strain);
        for (LivingEntity target : targetsInRoom(level, levelState, reservoir.room())) {
            if (AirVentBlock.isProtected(level, target.blockPosition())) continue;
            InfectionData targetData = InfectionCapability.get(target);
            if (targetData == null) continue;
            float incoming = ProtectiveEquipment.incomingAirMultiplier(target);
            if (incoming <= 0.0F) continue;
            ExposureKey exposureKey = new ExposureKey(
                    target.getUUID(), reservoir.canonicalStrain());
            Exposure current = levelState.exposures.get(exposureKey);
            if (current == null || now - current.lastExposure() > 80L) {
                current = new Exposure(0.0F, now,
                        0.65F + level.getRandom().nextFloat() * 0.55F);
            }
            float progress = current.progress() + incrementBase * incoming;
            if (progress >= current.threshold()) {
                strain.applyToEntity(targetData, target);
                levelState.exposures.remove(exposureKey);
            } else {
                levelState.exposures.put(exposureKey,
                        new Exposure(progress, now, current.threshold()));
            }
        }
    }

    private static List<LivingEntity> targetsInRoom(ServerLevel level, LevelState state,
                                                     AirRoomScanner.Room room) {
        List<LivingEntity> cached = state.roomTargets.get(room.signature());
        if (cached != null) return cached;
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class, room.bounds(), LivingEntity::isAlive)) {
            BlockPos eye = BlockPos.containing(target.getX(), target.getEyeY(), target.getZ());
            if (room.contains(eye) || room.contains(target.blockPosition())) targets.add(target);
        }
        List<LivingEntity> result = List.copyOf(targets);
        state.roomTargets.put(room.signature(), result);
        return result;
    }

    private static void depositEnvironmentalReservoir(ServerLevel level,
                                                       Reservoir reservoir, long now) {
        StrainData strain = reservoir.strain();
        if (!net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(strain, InfectionType.ENVIRONMENTAL)
                || !BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL)
                || reservoir.concentration() < 0.25F) return;
        int cellCount = reservoir.room().cellCount();
        if (cellCount == 0) return;
        SurfaceContaminationData surfaces = SurfaceContaminationData.get(level);
        int attempts = Math.min(6, Math.max(1, cellCount / 64));
        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos air = BlockPos.of(reservoir.room().cellAt(
                    level.getRandom().nextInt(cellCount)));
            Direction direction = DIRECTIONS[level.getRandom().nextInt(DIRECTIONS.length)];
            BlockPos surface = air.relative(direction);
            if (level.getBlockState(surface).isAir()
                    || AirVentBlock.isProtected(level, surface)) continue;
            int lifetime = Math.min(Integer.MAX_VALUE / 2,
                    BioForgeServerConfig.surfaceLifetimeTicks()) * 2;
            surfaces.contaminate(surface, strain,
                    Math.min(1.0F, reservoir.concentration() * 0.65F), lifetime, now);
        }
    }

    private static float decayedConcentration(Reservoir reservoir, long now) {
        long elapsed = Math.max(0L, now - reservoir.lastUpdated());
        if (elapsed == 0L) return reservoir.concentration();
        double lifetime = Math.max(200.0D,
                BioForgeServerConfig.airborneReservoirLifetimeTicks());
        double effectiveLifetime = reservoir.room().outdoors()
                ? lifetime * 0.25D : lifetime;
        double retention = Math.pow(0.005D, elapsed / effectiveLifetime);
        return (float) (reservoir.concentration() * retention);
    }

    private static List<BlockPos> visualizationCells(AirRoomScanner.Room room,
                                                      BlockPos center, int limit) {
        if (room == null || room.cellCount() == 0) return List.of();
        ArrayList<BlockPos> cells = new ArrayList<>(room.cellCount());
        for (int index = 0; index < room.cellCount(); index++) {
            cells.add(BlockPos.of(room.cellAt(index)));
        }
        cells.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        int resultSize = Math.min(cells.size(), Math.max(1, limit));
        return List.copyOf(cells.subList(0, resultSize));
    }

    private static boolean intersects(AirRoomScanner.Room room, BlockPos center, int radius) {
        for (int index = 0; index < room.cellCount(); index++) {
            long packed = room.cellAt(index);
            if (Math.abs(BlockPos.getX(packed) - center.getX()) <= radius
                    && Math.abs(BlockPos.getY(packed) - center.getY()) <= radius
                    && Math.abs(BlockPos.getZ(packed) - center.getZ()) <= radius) return true;
        }
        return false;
    }

    private static java.util.Optional<Float> parseFloat(String raw) {
        try {
            return java.util.Optional.of(Float.parseFloat(raw));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static final class LevelState {
        private final Map<ReservoirKey, Reservoir> reservoirs = new HashMap<>();
        private final ArrayDeque<ReservoirKey> processingOrder = new ArrayDeque<>();
        private final Map<ExposureKey, Exposure> exposures = new HashMap<>();
        private final Map<Long, List<LivingEntity>> roomTargets = new HashMap<>();
        private long lastProcessedTick = Long.MIN_VALUE;
    }

    private record Reservoir(StrainData strain, String canonicalStrain,
                             AirRoomScanner.Room room, float concentration,
                             long lastUpdated, long lastEmission,
                             long lastProcessed,
                             long lastEnvironmentalDeposit) {
        private Reservoir withConcentration(float value, long now) {
            return new Reservoir(strain, canonicalStrain, room,
                    value, now, lastEmission, lastProcessed, lastEnvironmentalDeposit);
        }

        private Reservoir withProcessedConcentration(float value, long now) {
            return new Reservoir(strain, canonicalStrain, room,
                    value, now, lastEmission, now, lastEnvironmentalDeposit);
        }

        private Reservoir withEnvironmentalDeposit(long now) {
            return new Reservoir(strain, canonicalStrain, room,
                    concentration, lastUpdated, lastEmission, lastProcessed, now);
        }
    }

    private record ReservoirKey(long roomSignature, String canonicalStrain) {}
    private record ExposureKey(UUID target, String canonicalStrain) {}
    private record Exposure(float progress, long lastExposure, float threshold) {}
    public record ScanResult(int reservoirs, float maximumConcentration, boolean outdoors,
                             List<BlockPos> visualizationCells) {}
}
