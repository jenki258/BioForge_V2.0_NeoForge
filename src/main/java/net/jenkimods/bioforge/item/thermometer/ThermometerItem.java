package net.jenkimods.bioforge.item.thermometer;

import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.clipboard.ClipboardHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ThermometerItem extends Item {

    private static final double NORMAL_TEMP_CELSIUS = 36.6;
    private static final double HIGH_TEMP_CELSIUS   = 38.5;
    private static final double LOW_TEMP_CELSIUS    = 35.5;

    private static final String NBT_READY       = "ThermometerReady";
    private static final String NBT_LAST_C      = "LastTempC";
    private static final String NBT_LAST_F      = "LastTempF";
    private static final String NBT_LAST_K      = "LastTempK";
    private static final String NBT_LAST_USED   = "LastUsedTime";
    private static final String NBT_LAST_TARGET = "LastTargetName";
    private static final String NBT_LAST_RAW_C  = "LastTempRaw";

    private static final long COOLDOWN_MS = 3000L;

    public static final TagKey<net.minecraft.world.entity.EntityType<?>> NO_THERMOMETER_TAG =
            TagKey.create(
                    net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    ResourceLocation.tryBuild("bioforge", "no_thermometer")
            );

    public ThermometerItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    public static boolean isReady(ItemStack stack) {
        return net.jenkimods.bioforge.util.StackData.has(stack) && net.jenkimods.bioforge.util.StackData.copy(stack).getBoolean(NBT_READY);
    }

    private void setReady(ItemStack stack, boolean ready) {
        net.jenkimods.bioforge.util.StackData.update(stack,
                tag -> tag.putBoolean(NBT_READY, ready));
    }

    private boolean isOnCooldown(ItemStack stack) {
        if (!net.jenkimods.bioforge.util.StackData.has(stack) || !net.jenkimods.bioforge.util.StackData.copy(stack).contains(NBT_LAST_USED)) return false;
        return System.currentTimeMillis() - net.jenkimods.bioforge.util.StackData.copy(stack).getLong(NBT_LAST_USED) < COOLDOWN_MS;
    }

    private void applyInternalCooldown(ItemStack stack) {
        net.jenkimods.bioforge.util.StackData.update(stack,
                tag -> tag.putLong(NBT_LAST_USED, System.currentTimeMillis()));
    }

    private double saveAllTemps(ItemStack stack, boolean tempPlus, boolean tempMinus) {
        double celsius = tempPlus ? HIGH_TEMP_CELSIUS : tempMinus ? LOW_TEMP_CELSIUS : NORMAL_TEMP_CELSIUS;
        net.jenkimods.bioforge.util.StackData.update(stack, tag -> {
            tag.putDouble(NBT_LAST_RAW_C, celsius);
            tag.putString(NBT_LAST_C, String.format("%.1f\u00b0C", celsius));
            tag.putString(NBT_LAST_F,
                    String.format("%.1f\u00b0F", celsius * 9.0 / 5.0 + 32.0));
            tag.putString(NBT_LAST_K, String.format("%.2fK", celsius + 273.15));
        });
        return celsius;
    }

    private boolean hasReading(ItemStack stack) {
        return net.jenkimods.bioforge.util.StackData.has(stack) && net.jenkimods.bioforge.util.StackData.copy(stack).contains(NBT_LAST_RAW_C);
    }

    private boolean isHighTemp(ItemStack stack) {
        if (!net.jenkimods.bioforge.util.StackData.has(stack) || !net.jenkimods.bioforge.util.StackData.copy(stack).contains(NBT_LAST_RAW_C)) return false;
        return net.jenkimods.bioforge.util.StackData.copy(stack).getDouble(NBT_LAST_RAW_C) >= HIGH_TEMP_CELSIUS;
    }

    private boolean isLowTemp(ItemStack stack) {
        if (!net.jenkimods.bioforge.util.StackData.has(stack) || !net.jenkimods.bioforge.util.StackData.copy(stack).contains(NBT_LAST_RAW_C)) return false;
        return net.jenkimods.bioforge.util.StackData.copy(stack).getDouble(NBT_LAST_RAW_C) <= LOW_TEMP_CELSIUS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (isOnCooldown(stack)) {
            if (level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.not_ready"));
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!isReady(stack)) {
            if (level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.shake_first"));
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide()) {
            applyReading(stack, player, player);
            applyInternalCooldown(stack);
            setReady(stack, false);
            player.setItemInHand(hand, stack);
            player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.reading_taken_self"));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();

        if (!player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.hold_shift"));
            }
            return InteractionResult.PASS;
        }

        if (isOnCooldown(stack)) {
            if (level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.not_ready"));
            }
            return InteractionResult.FAIL;
        }

        if (!isReady(stack)) {
            if (level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.shake_first"));
            }
            return InteractionResult.FAIL;
        }

        if (target.getType().is(NO_THERMOMETER_TAG) || target.getType().is(BioForgeTags.NO_DIAGNOSTICS)) {
            if (level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.no_infection", target.getDisplayName()));
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            applyReading(stack, player, target);
            applyInternalCooldown(stack);
            setReady(stack, false);
            player.setItemInHand(hand, stack);
            player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.reading_taken_mob", target.getDisplayName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public void applyReading(ItemStack stack, Player player, LivingEntity subject) {
        InfectionData data = InfectionCapability.get(subject);
        boolean tempPlus  = data != null && data.getSymptom(BioForgeSymptoms.TEMPERATURE_PLUS);
        boolean tempMinus = data != null && data.getSymptom(BioForgeSymptoms.TEMPERATURE_MINUS);

        double celsius = saveAllTemps(stack, tempPlus, tempMinus);
        net.jenkimods.bioforge.util.StackData.update(stack,
                tag -> tag.putString(NBT_LAST_TARGET,
                        subject.getDisplayName().getString()));
        ClipboardHelper.recordTemperature((float) celsius, false, player, subject.getUUID());
    }

    public void onShake(Player player, ItemStack stack) {
        if (!isReady(stack)) {
            setReady(stack, true);
            InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            player.setItemInHand(hand, stack);
            if (player.level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("item.bioforge.thermometer.shaken"));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (hasReading(stack)) {
            ChatFormatting tempColor = isHighTemp(stack) ? ChatFormatting.RED
                    : isLowTemp(stack) ? ChatFormatting.AQUA
                    : ChatFormatting.GREEN;

            boolean hasTarget = net.jenkimods.bioforge.util.StackData.has(stack) && net.jenkimods.bioforge.util.StackData.copy(stack).contains(NBT_LAST_TARGET);
            if (hasTarget) {
                tooltip.add(Component.translatable("item.bioforge.thermometer.reading_header_target",
                        net.jenkimods.bioforge.util.StackData.copy(stack).getString(NBT_LAST_TARGET)).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("item.bioforge.thermometer.reading_header")
                        .withStyle(ChatFormatting.GRAY));
            }

            tooltip.add(Component.translatable("item.bioforge.thermometer.temp_c",
                    net.jenkimods.bioforge.util.StackData.copy(stack).getString(NBT_LAST_C)).withStyle(tempColor));
            tooltip.add(Component.translatable("item.bioforge.thermometer.temp_f",
                    net.jenkimods.bioforge.util.StackData.copy(stack).getString(NBT_LAST_F)).withStyle(tempColor));
            tooltip.add(Component.translatable("item.bioforge.thermometer.temp_k",
                    net.jenkimods.bioforge.util.StackData.copy(stack).getString(NBT_LAST_K)).withStyle(tempColor));

            tooltip.add(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (isReady(stack)) {
            tooltip.add(Component.translatable("item.bioforge.thermometer.status_ready")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("item.bioforge.thermometer.status_used")
                    .withStyle(ChatFormatting.RED));
        }

        tooltip.add(Component.literal("─────────────────").withStyle(ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable("item.bioforge.thermometer.tooltip.shake")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.thermometer.tooltip.use_self")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.thermometer.tooltip.use_mob")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
