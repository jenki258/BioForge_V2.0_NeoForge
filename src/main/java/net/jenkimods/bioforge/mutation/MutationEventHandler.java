package net.jenkimods.bioforge.mutation;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;






@EventBusSubscriber(modid = BioForge.MODID)
public final class MutationEventHandler {
    private static final TagKey<Item> MEAT_FOODS = TagKey.create(Registries.ITEM,
            ResourceLocation.tryBuild(BioForge.MODID, "foods/meat"));
    private static final String CRAVING_MESSAGE_COOLDOWN = "BioForgeFleshCravingMessage";

    private MutationEventHandler() {}

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
                || entity.level().isClientSide()) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data == null || !data.isInfectionActive()
                || data.getSymptoms().getMutations().isEmpty()) return;
        MutationManager.tickMutations(data, entity);
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity().level().isClientSide()
                || event.getItem().getFoodProperties(event.getEntity()) == null
                || event.getItem().is(MEAT_FOODS)) return;
        InfectionData data = InfectionCapability.get(event.getEntity());
        if (data == null || !data.isInfectionActive()
                || !MutationManager.hasMutation(data, "flesh_cravings")) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            long gameTime = player.level().getGameTime();
            long nextMessage = player.getPersistentData().getLong(CRAVING_MESSAGE_COOLDOWN);
            if (gameTime >= nextMessage) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.mutation.flesh_cravings.reject"), true);
                player.getPersistentData().putLong(CRAVING_MESSAGE_COOLDOWN, gameTime + 40L);
            }
        }
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide() || !event.getItem().is(MEAT_FOODS)) return;
        InfectionData data = InfectionCapability.get(event.getEntity());
        if (data == null || !data.isInfectionActive()
                || !MutationManager.hasMutation(data, "flesh_cravings")) return;
        event.getEntity().heal(1.0F);
    }
}
