package net.jenkimods.bioforge.infection.lifecycle;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.infection.InfectionLifecycleDefinition;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionEventHandler;
import net.jenkimods.bioforge.infection.InfectionStore;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public final class InfectionLifecycleHandler {
    private static final float COLD_TEMPERATURE = 0.25F;
    private static final float HOT_TEMPERATURE = 1.2F;

    private InfectionLifecycleHandler() {}

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data == null || !data.isInfected()
                || Math.floorMod(entity.tickCount + entity.getId(), 20) != 0) return;

        InfectionLifecycleState state = data.getLifecycle();
        InfectionLifecycleDefinition profile = InfectionLifecycleRegistry.INSTANCE.resolve(state.profileId());
        boolean wasActive = !data.isIncubating();
        state.advanceAge(20L);
        if (wasActive) state.advanceActiveAge(20L);

        float temperature = level.getBiome(entity.blockPosition()).value().getBaseTemperature();
        boolean hot = temperature >= HOT_TEMPERATURE;
        boolean cold = temperature <= COLD_TEMPERATURE;
        float climateRate = 1.0F;
        if ((hot && !MutationManager.hasMutationTag(data, "heat_immunity"))
                || (cold && !MutationManager.hasMutationTag(data, "cold_immunity"))) {
            float adaptation = Math.min(1.0F, profile.adaptationSpeed());
            climateRate = profile.hostileClimateIncubationRate()
                    + (1.0F - profile.hostileClimateIncubationRate()) * adaptation;
        }
        if (data.isIncubating()) state.advanceIncubation(20.0F * climateRate);

        float points = profile.adaptationPointsPerSecond() * profile.adaptationSpeed();
        if (hot && !MutationManager.hasMutationTag(data, "heat_immunity")) {
            state.addHotPoints(points);
            if (state.hotAdaptationPoints() >= profile.hotAdaptationThreshold()) {
                applyClimateMutation(profile.hotAdaptationMutation(), data, entity);
            }
        }
        if (cold && !MutationManager.hasMutationTag(data, "cold_immunity")) {
            state.addColdPoints(points);
            if (state.coldAdaptationPoints() >= profile.coldAdaptationThreshold()) {
                applyClimateMutation(profile.coldAdaptationMutation(), data, entity);
            }
        }
        if (MutationManager.hasMutationTag(data, "heat_immunity")
                && MutationManager.hasMutationTag(data, "cold_immunity")) {
            applyClimateMutation(profile.dualAdaptationMutation(), data, entity);
        }

        if (!wasActive && !data.isIncubating()) {
            MutationManager.activateIncubatedMutations(data, entity);
            sync(entity, data);
        }

        int lifespanTicks = state.effectiveLifespanTicks(profile.lifespanTicks());
        if (state.selfDestructRequested()
                || (lifespanTicks >= 0 && state.activeAgeTicks() >= lifespanTicks)) {
            MutationManager.clearMutations(data, entity);
            data.clearInfection();
            if (entity instanceof ServerPlayer player) {
                InfectionStore.get(player.serverLevel()).clearInfection(player.getUUID());
            }
            sync(entity, data);
        }
    }

    private static void applyClimateMutation(String mutationId, InfectionData data,
                                             LivingEntity entity) {
        if (mutationId == null || mutationId.isBlank() || MutationManager.hasMutation(data, mutationId)) return;
        MutationLoader.INSTANCE.getMutation(mutationId).ifPresent(definition ->
                MutationManager.applyMutation(definition, data, entity, true));
    }

    private static void sync(LivingEntity entity, InfectionData data) {
        if (entity instanceof ServerPlayer player) InfectionEventHandler.syncToClient(player, data);
    }
}
