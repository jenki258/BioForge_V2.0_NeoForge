package net.jenkimods.bioforge.item.needle;

import net.jenkimods.bioforge.blood.*;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledge;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledgeStore;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.spread.ProtectiveEquipment;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.util.NbtObfuscator.ObfuscatedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SyringeItem extends Item {

    private static final int BLOOD_DRAIN = 10;
    private static final int BLOOD_TRANSFER = 2;
    public static final int MAX_USES = 4;

    private static final Map<UUID, Long> lastMobInjectionTick = new HashMap<>();

    public SyringeItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        EquipmentSlot oppositeSlot = (hand == InteractionHand.MAIN_HAND) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        if (!player.getItemBySlot(oppositeSlot).isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) return super.use(level, player, hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
        if (ProtectiveEquipment.blocksSyringes(player)) {
            return InteractionResultHolder.fail(stack);
        }

        Long lastMobTick = lastMobInjectionTick.get(sp.getUUID());
        if (lastMobTick != null && (System.currentTimeMillis() - lastMobTick) < 50) {
            return InteractionResultHolder.pass(stack);
        }

        if (isLookingAtLivingEntity(player)) {
            return InteractionResultHolder.pass(stack);
        }

        if (BloodSampleUtil.hasBlood(stack)) {
            BloodData selfData = BloodCapability.get(sp);
            if (selfData == null) return InteractionResultHolder.fail(stack);

            selfData.addBlood(BLOOD_TRANSFER);
            applyStoredInfection(stack, player);
            consumeUse(stack);

            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
            return InteractionResultHolder.success(stack);
        }

        BloodData selfData = BloodCapability.get(sp);
        if (selfData == null || selfData.getBlood() <= 0) return InteractionResultHolder.fail(stack);
        applyStoredInfection(stack, player);

        int newBlood = Math.max(0, selfData.getBlood() - BLOOD_DRAIN);
        selfData.setBlood(newBlood);
        if (newBlood > 0) {
            storeBlood(stack, selfData.getBlood(), selfData.getBloodType(),
                    sp.getName().getString(), sp.getUUID());
            captureInfection(stack, sp);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity living, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide()) return super.interactLivingEntity(stack, player, living, hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.FAIL;
        if (ProtectiveEquipment.blocksSyringes(living)) return InteractionResult.FAIL;

        if (!BloodSampleUtil.hasBlood(stack)) return InteractionResult.PASS;

        lastMobInjectionTick.put(sp.getUUID(), System.currentTimeMillis());

        BloodData targetData = BloodCapability.get(living);
        if (targetData == null) return InteractionResult.FAIL;

        targetData.addBlood(BLOOD_TRANSFER);
        applyStoredInfection(stack, living);
        consumeUse(stack);

        player.setItemInHand(hand, stack);
        level.playSound(null, living.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    public boolean tryExtractBlood(ItemStack stack, LivingEntity target, ServerPlayer attacker) {
        if (BloodSampleUtil.hasBlood(stack)) return false;
        if (ProtectiveEquipment.blocksSyringes(target)) return false;
        if (!NeedleItem.entityHasBlood(target)) return false;

        BloodData targetData = BloodCapability.get(target);
        if (targetData == null || targetData.getBlood() <= 0) return false;
        applyStoredInfection(stack, target);
        int newBlood = Math.max(0, targetData.getBlood() - BLOOD_DRAIN);
        targetData.setBlood(newBlood);
        if (newBlood > 0) {
            storeBlood(stack, targetData.getBlood(), targetData.getBloodType(),
                    target.getName().getString(), target.getUUID());
            captureInfection(stack, target);
            return true;
        }
        return false;
    }

    private static boolean isLookingAtLivingEntity(Player player) {
        HitResult hit = player.pick(5.0D, 0.0F, false);
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity;
    }

    public static void consumeUse(ItemStack stack) {
        ObfuscatedData data = BloodSampleUtil.getData(stack);
        if (data == null) return;
        int uses = data.amount() - 1;
        if (uses <= 0) {
            BloodSampleUtil.clear(stack);
        } else {
            BloodType type = BloodType.fromName(data.typeName());
            BloodSampleUtil.setData(stack, uses, type, data.sourceName(), data.subjectUUID());
        }
    }

    private static void storeBlood(ItemStack stack, int amount, BloodType type, String sourceName, UUID subjectUUID) {
        BloodSampleUtil.setData(stack, MAX_USES, type, sourceName, subjectUUID);
    }

    private static void captureInfection(ItemStack stack, LivingEntity entity) {
        InfectionData inf = InfectionCapability.get(entity);
        if (inf == null || !inf.isInfected()) return;
        if (!BioForgeServerConfig.isTransmissionEnabled(InfectionType.BLOOD)) return;
        if (!net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(inf, InfectionType.BLOOD)) return;
        String strain = buildStrainPayload(inf);
        NbtObfuscator.writeInfection(stack, strain);
    }

    private static String buildStrainPayload(InfectionData data) {
        if (data == null || !data.isInfected()) return "";
        StrainData strain = StrainData.buildFrom(data);
        strain.setColonyId(null);
        return strain.toPayload();
    }

    private static void applyStoredInfection(ItemStack stack, LivingEntity target) {
        String strainRaw = NbtObfuscator.readInfection(stack);
        if (strainRaw == null || strainRaw.isEmpty()) return;
        StrainData strain = StrainData.parse(strainRaw);
        if (!BioForgeServerConfig.isTransmissionEnabled(InfectionType.BLOOD)
                || !net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(strain, InfectionType.BLOOD)) return;
        InfectionData data = InfectionCapability.get(target);
        if (data != null && target.getRandom().nextFloat()
                < BioForgeServerConfig.bloodExposureChance()) {
            strain.applyToEntity(data, target);
        }
    }

    public static void clearInfection(ItemStack stack) {
        NbtObfuscator.clearInfection(stack);
    }

    public static boolean hasBlood(ItemStack stack) { return BloodSampleUtil.hasBlood(stack); }
    public static int getUses(ItemStack stack) {
        ObfuscatedData data = BloodSampleUtil.getData(stack);
        return data != null ? data.amount() : 0;
    }

    @Nullable public static BloodType getBloodType(ItemStack stack) {
        ObfuscatedData data = BloodSampleUtil.getData(stack);
        return data != null ? BloodType.fromName(data.typeName()) : null;
    }
    @Nullable public static String getSourceName(ItemStack stack) {
        ObfuscatedData data = BloodSampleUtil.getData(stack);
        return data != null ? data.sourceName() : null;
    }
    @Nullable public static UUID getSubjectUUID(ItemStack stack) {
        ObfuscatedData data = BloodSampleUtil.getData(stack);
        return data != null ? data.subjectUUID() : null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!hasBlood(stack)) {
            tooltip.add(Component.translatable("item.bioforge.syringe.empty").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("item.bioforge.syringe.tooltip.draw_self").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.bioforge.syringe.tooltip.draw_mob").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.bioforge.syringe.tooltip.warning_blood").withStyle(ChatFormatting.DARK_RED));
            return;
        }

        ObfuscatedData data = BloodSampleUtil.getData(stack);
        if (data == null) return;
        tooltip.add(Component.translatable("item.bioforge.syringe.filled").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.bioforge.syringe.source", data.sourceName()).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.bioforge.syringe.uses_left", data.amount()).withStyle(ChatFormatting.GOLD));
        appendKnowledgeLines(data, tooltip);
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("item.bioforge.syringe.tooltip.inject_self").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.syringe.tooltip.inject_mob").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.syringe.tooltip.warning_blood").withStyle(ChatFormatting.DARK_RED));
    }

    @OnlyIn(Dist.CLIENT)
    private static void appendKnowledgeLines(ObfuscatedData data, List<Component> tooltip) {
        if (data.subjectUUID() == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        BloodKnowledgeStore store = BloodKnowledgeStore.get(server);
        Optional<BloodKnowledge> knowledge = store.find(mc.player.getUUID(), data.subjectUUID());
        if (knowledge.isEmpty()) return;
        BloodKnowledge k = knowledge.get();
        if (k.getAntiA() != null && k.getAntiB() != null && k.getAntiD() != null) {
            BloodType type = BloodType.fromName(data.typeName());
            tooltip.add(Component.translatable("item.bioforge.syringe.blood_type",
                    type.getDisplayNameComponent()).withStyle(ChatFormatting.DARK_RED));
        }
        tooltip.add(Component.translatable("item.bioforge.syringe.reactions").withStyle(ChatFormatting.DARK_GREEN));
        if (k.getAntiA() != null) {
            tooltip.add(Component.translatable("item.bioforge.syringe.anti_a",
                    k.getAntiA() ? "+" : "-").withStyle(k.getAntiA() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if (k.getAntiB() != null) {
            tooltip.add(Component.translatable("item.bioforge.syringe.anti_b",
                    k.getAntiB() ? "+" : "-").withStyle(k.getAntiB() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if (k.getAntiD() != null) {
            tooltip.add(Component.translatable("item.bioforge.syringe.anti_d",
                    k.getAntiD() ? "+" : "-").withStyle(k.getAntiD() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }
}
