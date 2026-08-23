package net.jenkimods.bioforge.item.infection;

import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SwabItem extends Item {

    public SwabItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isContaminated(stack)) return InteractionResultHolder.fail(stack);

        if (!level.isClientSide()) {
            InfectionData data = InfectionCapability.get(player);
            String payload;
            if (data != null && data.isInfected()) {
                payload = buildStrainPayload(data);
            } else {
                payload = "CLEAN";
            }

            NbtObfuscator.writeString(stack, payload);
            player.setItemInHand(hand, stack);

            if ("CLEAN".equals(payload)) {
                player.sendSystemMessage(Component.translatable("item.bioforge.swab.collected_clean_self"));
            } else {
                player.sendSystemMessage(Component.translatable("item.bioforge.swab.collected_self"));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (isContaminated(stack)) return InteractionResult.FAIL;

        if (target.getType().is(BioForgeTags.NO_DIAGNOSTICS)) {
            player.sendSystemMessage(Component.translatable("item.bioforge.swab.blocked",
                    target.getDisplayName()));
            return InteractionResult.FAIL;
        }

        InfectionData data = InfectionCapability.get(target);
        String payload;
        if (data != null && data.isInfected()) {
            payload = buildStrainPayload(data);
        } else {
            payload = "CLEAN";
        }

        NbtObfuscator.writeString(stack, payload);
        player.setItemInHand(hand, stack);

        if ("CLEAN".equals(payload)) {
            player.sendSystemMessage(Component.translatable("item.bioforge.swab.collected",
                    target.getDisplayName()));
        } else {
            player.sendSystemMessage(Component.translatable("item.bioforge.swab.collected",
                    target.getDisplayName()));
        }
        return InteractionResult.CONSUME;
    }

    private String buildStrainPayload(@Nullable InfectionData data) {
        if (data == null || !data.isInfected()) return "CLEAN";
        StrainData strain = StrainData.buildFrom(data);
        strain.setColonyId(null);
        return strain.toPayload();
    }

    public static boolean isContaminated(ItemStack stack) {
        return NbtObfuscator.readString(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        if (!isContaminated(stack)) {
            tooltip.add(Component.translatable("item.bioforge.swab.tooltip.clean")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.swab.tooltip.usage")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.bioforge.swab.tooltip.contaminated")
                    .withStyle(ChatFormatting.RED));
        }
    }
}