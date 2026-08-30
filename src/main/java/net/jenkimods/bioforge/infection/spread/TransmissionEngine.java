package net.jenkimods.bioforge.infection.spread;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.jenkimods.bioforge.api.definition.TransmissionDefinition;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.LungSound;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = BioForge.MODID)
public final class TransmissionEngine {
    private static final Map<ServerLevel, Map<UUID, SurfaceExposure>> LAST_SURFACE_EXPOSURES =
            new WeakHashMap<>();

    private TransmissionEngine() {}

    public static void clearCaches() {
        LAST_SURFACE_EXPOSURES.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now % 20L == 0L) {
            AirborneReservoirManager.tick(level);
            AirRoomScanner.maintain(level, now);
        }
        if (now % 200L == 0L) {
            SurfaceContaminationData.get(level).purgeExpired(
                    now, BioForgeServerConfig.surfaceCleanupBudget());
            Map<UUID, SurfaceExposure> exposures = LAST_SURFACE_EXPOSURES.get(level);
            if (exposures != null) {
                exposures.values().removeIf(exposure -> now - exposure.gameTime() > 400L);
                if (exposures.isEmpty()) LAST_SURFACE_EXPOSURES.remove(level);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (!BioForgeServerConfig.spreadingEnabled()) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data != null && data.isInfected()
                && data.isContagious()
                && Math.floorMod(entity.tickCount + entity.getId(), 20) == 0) {
            emitFromHost(level, entity, data, now);
        }
        if (data != null
                && Math.floorMod(entity.tickCount + entity.getId(), 10) == 0) {
            exposeFromSurface(level, entity, entity.blockPosition().below(), ExposureKind.STEP, now);
            if (data.isContagious()) depositFromStep(level, entity, data, now);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        long now = level.getGameTime();
        AirRoomScanner.invalidate(level);
        exposeFromSurface(level, player, event.getPos(), ExposureKind.INTERACT, now);
        depositFromInteraction(level, player, event.getPos(), now);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        exposeFromSurface(level, event.getEntity(), event.getPos(), ExposureKind.INTERACT,
                level.getGameTime());
        depositFromInteraction(level, event.getEntity(), event.getPos(), level.getGameTime());
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        AirRoomScanner.invalidate(level);
        exposeFromSurface(level, event.getPlayer(), event.getPos(), ExposureKind.BREAK,
                level.getGameTime());
        SurfaceContaminationData.get(level).removeEthanolCoating(event.getPos());
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AirRoomScanner.invalidate(level);
            SurfaceContaminationData.get(level).removeEthanolCoating(event.getPos());
        }
    }

    private static void emitFromHost(ServerLevel level, LivingEntity host,
                                     InfectionData data, long now) {
        float strength = Math.max(0.0F, data.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));
        float infectivity = InfectionLifecycleRegistry.INSTANCE.infectivity(data);
        runAddonBehaviors(level, host, data);
        boolean airborne = BioForgeDefinitionManager.hasTransmissionBehavior(
                data, InfectionType.AIR_BORNE)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.AIR_BORNE);
        boolean contact = BioForgeDefinitionManager.hasTransmissionBehavior(
                data, InfectionType.CONTACT_BASED)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.CONTACT_BASED);
        boolean environmental = BioForgeDefinitionManager.hasTransmissionBehavior(
                data, InfectionType.ENVIRONMENTAL)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL);
        StrainData strain = null;
        if (airborne) {
            float outward = ProtectiveEquipment.outgoingAirMultiplier(host);
            if (outward > 0.0F) {
                strain = StrainData.buildFrom(data);
                BlockPos eye = BlockPos.containing(host.getX(), host.getEyeY(), host.getZ());
                AirRoomScanner.Room room = AirRoomScanner.scan(level, eye);
                float respiratory = data.getSymptom(BioForgeSymptoms.LUNG_SOUND)
                        == LungSound.CRACKLE ? 1.35F : 1.0F;
                AirborneReservoirManager.emit(level, room, strain,
                        outward * (0.45F + strength) * respiratory * infectivity, eye);
            }
        }

        if ((!contact && !environmental)
                || ProtectiveEquipment.outgoingContactMultiplier(host) <= 0.0F) return;
        float chance = BioForgeServerConfig.surfaceDepositChance();
        if (environmental) chance += 0.18F;
        if (contact && BioForgeDefinitionManager.hasTransmissionBehavior(data, InfectionType.AIR_BORNE)) chance += 0.12F;
        if (level.getRandom().nextFloat() >= Math.min(1.0F,
                chance * (0.5F + strength) * infectivity)) return;
        int lifetime = BioForgeServerConfig.surfaceLifetimeTicks();
        if (environmental) lifetime = Math.min(Integer.MAX_VALUE / 3, lifetime) * 3;
        if (strain == null) strain = StrainData.buildFrom(data);
        SurfaceContaminationData.get(level).contaminate(
                host.blockPosition().below(), strain,
                Math.min(1.0F, 0.3F + strength * 0.7F), lifetime, now);
    }

    private static void depositFromInteraction(ServerLevel level, Player player,
                                               BlockPos pos, long now) {
        InfectionData data = InfectionCapability.get(player);
        if (data == null || !data.isContagious()
                || ProtectiveEquipment.outgoingContactMultiplier(player) <= 0.0F) return;
        boolean contact = BioForgeDefinitionManager.hasTransmissionBehavior(data, InfectionType.CONTACT_BASED)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.CONTACT_BASED);
        boolean environmental = BioForgeDefinitionManager.hasTransmissionBehavior(data, InfectionType.ENVIRONMENTAL)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL);
        if (!contact && !environmental) return;
        float strength = Math.max(0.0F, data.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));
        int lifetime = BioForgeServerConfig.surfaceLifetimeTicks();
        if (environmental) lifetime = Math.min(Integer.MAX_VALUE / 3, lifetime) * 3;
        StrainData strain = StrainData.buildFrom(data);
        SurfaceContaminationData surfaces = SurfaceContaminationData.get(level);
        float depositStrength = Math.min(1.0F, 0.45F + strength * 0.55F);
        surfaces.contaminate(pos, strain, depositStrength, lifetime, now);
        surfaces.contaminate(pos.above(), strain, depositStrength * 0.8F, lifetime, now);
        surfaces.contaminate(pos.below(), strain, depositStrength * 0.65F, lifetime, now);
        for (net.minecraft.core.Direction direction
                : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            surfaces.contaminate(pos.relative(direction), strain,
                    depositStrength * 0.55F, lifetime, now);
        }
    }

    private static void depositFromStep(ServerLevel level, LivingEntity entity,
                                        InfectionData data, long now) {
        if (ProtectiveEquipment.outgoingContactMultiplier(entity) <= 0.0F
                || !BioForgeDefinitionManager.hasTransmissionBehavior(
                data, InfectionType.CONTACT_BASED)
                || !BioForgeServerConfig.isTransmissionEnabled(
                InfectionType.CONTACT_BASED)) return;
        float strength = Math.max(0.0F,
                data.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));
        SurfaceContaminationData.get(level).contaminate(
                entity.blockPosition().below(), StrainData.buildFrom(data),
                Math.min(0.75F, 0.2F + strength * 0.45F),
                BioForgeServerConfig.surfaceLifetimeTicks(), now);
    }

    private static void exposeFromSurface(ServerLevel level, LivingEntity target,
                                          BlockPos pos, ExposureKind kind, long now) {
        if (!BioForgeServerConfig.spreadingEnabled()) return;
        InfectionData targetData = InfectionCapability.get(target);
        if (targetData == null) return;
        SurfaceContaminationData surfaces = SurfaceContaminationData.get(level);
        SurfaceContaminationData.Contamination contamination = surfaces
                .contaminationAt(pos, now).orElse(null);
        if (contamination == null) return;
        Map<UUID, SurfaceExposure> exposures = LAST_SURFACE_EXPOSURES.computeIfAbsent(
                level, ignored -> new HashMap<>());
        UUID exposureKey = target.getUUID();
        SurfaceExposure previous = exposures.get(exposureKey);
        if (previous != null && previous.position() == pos.asLong()
                && previous.kind() == kind && now - previous.gameTime() < 30L) return;
        exposures.put(exposureKey, new SurfaceExposure(pos.asLong(), kind, now));

        StrainData strain = surfaces.strainAt(pos, contamination);
        boolean contact = BioForgeDefinitionManager.hasTransmissionBehavior(
                strain, InfectionType.CONTACT_BASED)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.CONTACT_BASED);
        boolean environmental = BioForgeDefinitionManager.hasTransmissionBehavior(
                strain, InfectionType.ENVIRONMENTAL)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL);
        if (!contact && !environmental) return;
        float incoming = ProtectiveEquipment.incomingContactMultiplier(target);
        if (incoming <= 0.0F) return;
        float strainStrength = strain.getSymptom("infection_strength")
                .map(TransmissionEngine::parseFloat).orElse(0.5F);
        float kindMultiplier = kind == ExposureKind.STEP ? 0.55F
                : kind == ExposureKind.BREAK ? 1.25F : 1.0F;
        float chance = BioForgeServerConfig.surfaceExposureChance()
                * contamination.strength() * (0.5F + Math.max(0.0F, strainStrength))
                * kindMultiplier * incoming
                * InfectionLifecycleRegistry.INSTANCE.infectivity(strain);
        if (level.getRandom().nextFloat() < Math.min(1.0F, chance)) {
            strain.applyToEntity(targetData, target);
        }
        float weatherDecay = level.isRainingAt(pos.above()) ? 0.08F : 0.01F;
        surfaces.weaken(pos, weatherDecay + (kind == ExposureKind.BREAK ? 0.2F : 0.0F), now);
    }

    private static float parseFloat(String raw) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return 0.5F;
        }
    }

    private static void runAddonBehaviors(ServerLevel level, LivingEntity host, InfectionData data) {
        for (ResourceLocation transmissionId : data.getTransmissionIds()) {
            TransmissionDefinition definition = BioForgeDefinitionManager
                    .transmission(transmissionId).orElse(null);
            if (definition == null) continue;
            for (ResourceLocation behaviorId : definition.behaviors()) {
                BioForgeBehaviorRegistry.transmission(behaviorId).ifPresent(behavior -> {
                    try {
                        behavior.tick(level, host, data, transmissionId, definition);
                    } catch (RuntimeException exception) {
                        BioForge.LOGGER.error("Transmission behavior {} failed for {}: {}",
                                behaviorId, transmissionId, exception.getMessage());
                    }
                });
            }
        }
    }

    private enum ExposureKind { STEP, INTERACT, BREAK }

    private record SurfaceExposure(long position, ExposureKind kind, long gameTime) {}
}
