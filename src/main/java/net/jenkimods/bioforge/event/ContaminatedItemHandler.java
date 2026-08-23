package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.spread.ItemStrainData;
import net.jenkimods.bioforge.infection.spread.SurfaceContaminationData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public final class ContaminatedItemHandler {
    private static final ThreadLocal<StrainData> BREWING_STRAIN = new ThreadLocal<>();

    private ContaminatedItemHandler() {}

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack output = event.getCrafting();
        if (output.isEmpty()) return;
        StrainData strongest = null;
        for (int slot = 0; slot < event.getInventory().getContainerSize(); slot++) {
            strongest = ItemStrainData.stronger(
                    strongest, ItemStrainData.read(event.getInventory().getItem(slot)));
        }
        if (strongest != null) ItemStrainData.write(output, strongest);
    }

    @SubscribeEvent
    public static void beforeBrewing(PotionBrewEvent.Pre event) {
        StrainData strongest = null;
        for (int slot = 0; slot < event.getLength(); slot++) {
            strongest = ItemStrainData.stronger(strongest,
                    ItemStrainData.read(event.getItem(slot)));
        }
        if (strongest != null
                && net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(strongest, InfectionType.WATER_BORNE)
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.WATER_BORNE)) {
            BREWING_STRAIN.set(strongest);
        } else {
            BREWING_STRAIN.remove();
        }
    }

    @SubscribeEvent
    public static void afterBrewing(PotionBrewEvent.Post event) {
        StrainData strain = BREWING_STRAIN.get();
        BREWING_STRAIN.remove();
        if (strain == null) return;
        for (int slot = 0; slot < Math.min(3, event.getLength()); slot++) {
            ItemStack output = event.getItem(slot);
            if (!output.isEmpty()) ItemStrainData.write(output, strain);
        }
    }

    @SubscribeEvent
    public static void onFillBottle(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !BioForgeServerConfig.isTransmissionEnabled(InfectionType.WATER_BORNE)) return;
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.GLASS_BOTTLE)) return;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(5.0D));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        SurfaceContaminationData.get(level).contaminationAt(
                hit.getBlockPos(), level.getGameTime()).ifPresent(contamination -> {
            StrainData strain = StrainData.parse(contamination.strainPayload());
            if (!net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                    .hasTransmissionBehavior(strain, InfectionType.WATER_BORNE)) return;
            ItemStack filled = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            ItemStrainData.write(filled, strain);
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filled));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, hit.getBlockPos());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        });
    }

    @SubscribeEvent
    public static void onAnimalDrops(LivingDropsEvent event) {
        LivingEntity animal = event.getEntity();
        if (animal instanceof Player || animal.level().isClientSide()
                || !BioForgeServerConfig.isTransmissionEnabled(InfectionType.ANIMALS)) return;
        InfectionData data = InfectionCapability.get(animal);
        if (data == null || !data.isInfected()
                || !net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(data, InfectionType.ANIMALS)) return;
        StrainData strain = StrainData.buildFrom(data);
        float strength = Math.max(0.0F, data.getSymptom(
                net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms.INFECTION_STRENGTH));
        event.getDrops().forEach(drop -> {
            boolean edible = drop.getItem().getFoodProperties(animal) != null;
            if (edible || animal.getRandom().nextFloat()
                    < Math.min(1.0F, 0.45F + strength * 0.55F)) {
                ItemStrainData.write(drop.getItem(), strain);
            }
        });
    }

    @SubscribeEvent
    public static void onAnimalProductSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity drop)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !drop.getItem().is(Items.EGG)
                || !BioForgeServerConfig.isTransmissionEnabled(InfectionType.ANIMALS)) return;
        StrainData strongest = null;
        for (Chicken chicken : level.getEntitiesOfClass(
                Chicken.class, drop.getBoundingBox().inflate(2.0D), LivingEntity::isAlive)) {
            InfectionData data = InfectionCapability.get(chicken);
            if (data == null || !data.isInfected()
                    || !net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                    .hasTransmissionBehavior(data, InfectionType.ANIMALS)) continue;
            strongest = ItemStrainData.stronger(strongest, StrainData.buildFrom(data));
        }
        if (strongest != null) ItemStrainData.write(drop.getItem(), strongest);
    }
}
