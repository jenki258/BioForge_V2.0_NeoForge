package net.jenkimods.bioforge.item.needle;

import net.jenkimods.bioforge.blood.*;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.spread.ProtectiveEquipment;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.util.NbtObfuscator.ObfuscatedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NeedleItem extends Item {

    public static final TagKey<EntityType<?>> NO_BLOOD_TAG = TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.tryBuild("bioforge", "no_blood")
    );

    public enum Tier {
        WOODEN  (0.25f,  8, 100, 1.0f),
        IRON    (0.50f, 16, 100, 1.0f),
        HARDENED(1.00f, 32, 100, 1.0f);

        public final float chance;
        public final int durability;
        public final int cooldownTicks;
        public final float selfDamage;

        Tier(float chance, int durability, int cooldownTicks, float selfDamage) {
            this.chance        = chance;
            this.durability    = durability;
            this.cooldownTicks = cooldownTicks;
            this.selfDamage    = selfDamage;
        }
    }

    private static final int BLOOD_DRAIN = 10;
    private static final int BLOOD_TRANSFER = 2;
    private static final Random RNG = new Random();

    public final Tier tier;

    public NeedleItem(Tier tier) {
        super(new Item.Properties().stacksTo(1).durability(tier.durability));
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        EquipmentSlot oppositeSlot = (hand == InteractionHand.MAIN_HAND) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        if (!player.getItemBySlot(oppositeSlot).isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
        if (ProtectiveEquipment.blocksSyringes(player)) {
            return InteractionResultHolder.fail(stack);
        }

        if (BloodSampleUtil.hasBlood(stack)) {
            BloodData selfData = BloodCapability.get(sp);
            if (selfData == null) return InteractionResultHolder.fail(stack);

            selfData.addBlood(BLOOD_TRANSFER);
            applyStoredInfection(stack, player);
            clearBlood(stack);

            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
            return InteractionResultHolder.success(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return InteractionResultHolder.fail(stack);

        BloodData selfData = BloodCapability.get(sp);
        if (selfData == null || selfData.getBlood() <= 0) return InteractionResultHolder.fail(stack);
        applyStoredInfection(stack, player);

        player.hurt(level.damageSources().generic(), tier.selfDamage);
        damageNeedle(stack, sp);
        sp.getCooldowns().addCooldown(this, tier.cooldownTicks);

        if (roll()) {
            if (selfData != null) {
                int newBlood = Math.max(0, selfData.getBlood() - BLOOD_DRAIN);
                selfData.setBlood(newBlood);
                if (newBlood > 0) {
                    storeBlood(stack, selfData.getBlood(), selfData.getBloodType(),
                            sp.getName().getString(), sp.getUUID());
                    captureInfection(stack, sp);
                }
            }
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.FAIL;
        if (ProtectiveEquipment.blocksSyringes(target)) return InteractionResult.FAIL;
        if (!BloodSampleUtil.hasBlood(stack)) return InteractionResult.PASS;

        BloodData targetData = BloodCapability.get(target);
        if (targetData == null) return InteractionResult.FAIL;
        targetData.addBlood(BLOOD_TRANSFER);
        applyStoredInfection(stack, target);
        clearBlood(stack);
        player.setItemInHand(hand, stack);
        level.playSound(null, target.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    public boolean tryExtractBlood(ItemStack stack, LivingEntity target, ServerPlayer attacker) {
        if (BloodSampleUtil.hasBlood(stack)) return false;
        if (ProtectiveEquipment.blocksSyringes(target)) return false;
        if (attacker.getCooldowns().isOnCooldown(this)) return false;
        if (!target.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return false;
        if (!entityHasBlood(target)) return false;

        BloodData targetData = BloodCapability.get(target);
        if (targetData == null || targetData.getBlood() <= 0) return false;
        applyStoredInfection(stack, target);
        damageNeedle(stack, attacker);
        attacker.getCooldowns().addCooldown(this, tier.cooldownTicks);

        if (roll()) {
            if (targetData != null) {
                int newBlood = Math.max(0, targetData.getBlood() - BLOOD_DRAIN);
                targetData.setBlood(newBlood);
                if (newBlood > 0) {
                    storeBlood(stack, targetData.getBlood(), targetData.getBloodType(),
                            target.getName().getString(), target.getUUID());
                    captureInfection(stack, target);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean entityHasBlood(LivingEntity entity) {
        return !entity.getType().is(NO_BLOOD_TAG);
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

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        BloodSampleUtil.appendSampleTooltip(
                stack,
                tooltip,
                "item.bioforge.needle.tooltip.empty",
                "item.bioforge.needle.tooltip.filled",
                "item.bioforge.needle.tooltip.source",
                null
        );
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("item.bioforge.needle.tooltip.use_self").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.needle.tooltip.use_other").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.needle.tooltip.warning_blood").withStyle(ChatFormatting.DARK_RED));
    }

    public static float getFilledPredicate(ItemStack stack) { return hasBlood(stack) ? 1.0f : 0.0f; }
    public static boolean hasBlood(ItemStack stack) { return BloodSampleUtil.hasBlood(stack); }
    public static int getBloodAmount(ItemStack stack) {
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
    public static void clearBlood(ItemStack stack) {
        BloodSampleUtil.clear(stack);
    }

    private boolean roll() { return tier.chance >= 1.0f || RNG.nextFloat() < tier.chance; }
    private static void storeBlood(ItemStack stack, int amount, BloodType type, String sourceName, UUID subjectUUID) {
        BloodSampleUtil.setData(stack, amount, type, sourceName, subjectUUID);
    }
    private static void damageNeedle(ItemStack stack, ServerPlayer player) {
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
    }
}
