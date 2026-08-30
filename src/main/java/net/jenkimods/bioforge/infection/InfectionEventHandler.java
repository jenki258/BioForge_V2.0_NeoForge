package net.jenkimods.bioforge.infection;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.network.InfectionNetworkHandler;
import net.jenkimods.bioforge.infection.network.InfectionSyncPacket;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.infection.spread.ProtectiveEquipment;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.jenkimods.bioforge.vaccine.StrainImmunityManager;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

@EventBusSubscriber(modid = BioForge.MODID)
public class InfectionEventHandler {

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data == null || data.getStrainImmunities().isEmpty()) return;
        boolean expired = data.tickStrainImmunities();
        if (expired || entity.tickCount % 20 == 0) {
            StrainImmunityManager.refreshStatusEffect(entity, data);
        }
        if (expired && entity instanceof ServerPlayer player) {
            syncToClient(player, data);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!BioForgeServerConfig.isTransmissionEnabled(InfectionType.ATTACK_BASED)) return;
        LivingEntity target = event.getEntity();
        if (target.getType().is(BioForgeTags.NO_INFECTION_ATTACK_SPREAD)) return;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
        if (attacker == null) return;
        InfectionData attackerData = InfectionCapability.get(attacker);
        if (attackerData == null || !attackerData.isContagious()) return;
        if (!BioForgeDefinitionManager.hasTransmissionBehavior(
                attackerData, InfectionType.ATTACK_BASED)) return;
        if (!BioForgeDefinitionManager.allowsTransmission(attackerData.getPathogenId(),
                net.jenkimods.bioforge.api.definition.BioForgeIds.transmission(InfectionType.ATTACK_BASED))) return;
        InfectionData targetData = InfectionCapability.get(target);
        if (targetData == null) return;
        if (ProtectiveEquipment.outgoingContactMultiplier(attacker) <= 0.0F
                || ProtectiveEquipment.incomingContactMultiplier(target) <= 0.0F) return;
        float strength = Math.max(0.0F,
                attackerData.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));
        float chance = BioForgeServerConfig.attackExposureChance() * (0.5F + strength)
                * InfectionLifecycleRegistry.INSTANCE.infectivity(attackerData);
        if (BioForgeDefinitionManager.hasTransmissionBehavior(attackerData, InfectionType.BLOOD)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.BLOOD)) {
            chance *= 1.35F;
        }
        if (attacker.getRandom().nextFloat() >= Math.min(1.0F, chance)) return;
        StrainData.buildFrom(attackerData).applyToEntity(targetData, target);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        Player oldPlayer = event.getOriginal();
        InfectionData oldData = InfectionCapability.get(oldPlayer);
        InfectionData newData = InfectionCapability.get(newPlayer);
        if (oldData == null || newData == null) return;
        newData.copyStrainImmunitiesFrom(oldData);
        ServerPlayer serverPlayer = (ServerPlayer) newPlayer;
        InfectionStore store = InfectionStore.get(serverPlayer.serverLevel());
        InfectionStore.InfectionRecord record = store.getInfection(serverPlayer.getUUID());
        boolean preserveAllInfections = serverPlayer.level().getGameRules()
                .getBoolean(BioForgeGameRules.PERSISTENT_INFECTIONS);
        if (preserveAllInfections && oldData.isInfected()) {
            newData.copyCompleteStateFrom(oldData);
            MutationManager.refreshContinuousEffects(newData, serverPlayer);
        } else if (record != null && record.persistent()) {
            newData.setInfected(true);
            if (record.pathogenId() != null) newData.setPathogenId(record.pathogenId());
            else newData.setPathogenType(record.pathogenType());
            if (!record.transmissionIds().isEmpty()) {
                for (net.minecraft.resources.ResourceLocation id : record.transmissionIds()) {
                    newData.addTransmissionId(id);
                }
            } else {
                for (InfectionType t : record.infectionTypes()) newData.addInfectionType(t);
            }
            for (Map.Entry<String, Object> entry : record.symptoms().entrySet()) {
                SymptomKey<?> key = BioForgeSymptoms.getAllSymptomKeys().get(entry.getKey());
                if (key != null) {
                    newData.getSymptoms().set((SymptomKey) key, entry.getValue());
                }
            }
            for (String mutationId : record.mutations()) {
                newData.getSymptoms().addMutation(mutationId);
            }
            newData.getLifecycle().setTimingOverridesRaw(
                    record.incubationTicksOverride(), record.lifespanTicksOverride());
            MutationManager.refreshContinuousEffects(newData, serverPlayer);
        } else {
            newData.clearInfection();
            if (record != null) store.clearInfection(serverPlayer.getUUID());
        }
        syncToClient(serverPlayer, newData);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        InfectionData data = InfectionCapability.get(player);
        if (data == null) return;
        MutationManager.refreshContinuousEffects(data, player);
        StrainImmunityManager.refreshStatusEffect(player, data);
        syncToClient(player, data);
    }

    public static void syncToClient(ServerPlayer player, InfectionData data) {
        InfectionNetworkHandler.sendDefinitionsIfChanged(player);
        InfectionNetworkHandler.sendToPlayer(InfectionSyncPacket.fromData(data), player);
    }
}
