package net.jenkimods.bioforge.item.samples;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledge;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledgeStore;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.item.needle.NeedleItem;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.util.NbtObfuscator.ObfuscatedData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class BloodSlideItem extends Item {

    public BloodSlideItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack slide = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.pass(slide);

        if (hasBlood(slide)) return InteractionResultHolder.fail(slide);

        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack tool = player.getItemInHand(other);

        if (!(tool.getItem() instanceof SyringeItem) && !(tool.getItem() instanceof NeedleItem)) {
            if (player instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.translatable("item.bioforge.blood_slide.need_blood"));
            return InteractionResultHolder.fail(slide);
        }

        if (!BloodSampleUtil.hasBlood(tool)) {
            if (player instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.translatable("item.bioforge.blood_slide.need_blood"));
            return InteractionResultHolder.fail(slide);
        }

        ObfuscatedData data = BloodSampleUtil.getData(tool);
        if (data == null) return InteractionResultHolder.fail(slide);
        BloodSampleUtil.setData(slide, data.amount(),
                BloodType.fromName(data.typeName()), data.sourceName(), data.subjectUUID());

        String infectionStrain = NbtObfuscator.readInfection(tool);
        if (infectionStrain == null || infectionStrain.isEmpty()) {
            infectionStrain = NbtObfuscator.readString(tool);
        }
        if (infectionStrain != null && !infectionStrain.isEmpty()) {
            NbtObfuscator.writeInfection(slide, infectionStrain);
        }

        if (tool.getItem() instanceof SyringeItem) {
            SyringeItem.consumeUse(tool);
        } else if (tool.getItem() instanceof NeedleItem) {
            NeedleItem.clearBlood(tool);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
        if (player instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.translatable("item.bioforge.blood_slide.transferred"));
        return InteractionResultHolder.success(slide);
    }

    public static boolean hasBlood(ItemStack stack) {
        return BloodSampleUtil.hasBlood(stack);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!hasBlood(stack)) {
            tooltip.add(Component.translatable("item.bioforge.blood_slide.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.blood_slide.tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        ObfuscatedData data = BloodSampleUtil.getData(stack);
        if (data == null) return;

        tooltip.add(Component.translatable("item.bioforge.blood_slide.filled").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.bioforge.blood_slide.source", data.sourceName()).withStyle(ChatFormatting.WHITE));
        BloodType type = BloodType.fromName(data.typeName());
        tooltip.add(Component.translatable("item.bioforge.blood_slide.blood_type", type.getDisplayNameComponent()).withStyle(ChatFormatting.DARK_RED));

        appendKnowledgeLines(data, tooltip);
    }

    @OnlyIn(Dist.CLIENT)
    private static void appendKnowledgeLines(ObfuscatedData data, List<Component> tooltip) {
        if (data.subjectUUID() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        BloodKnowledgeStore store = BloodKnowledgeStore.get(server);
        Optional<BloodKnowledge> knowledge = store.find(mc.player.getUUID(), data.subjectUUID());
        if (knowledge.isEmpty()) return;
        BloodKnowledge k = knowledge.get();
        if (k.getAntiA() != null && k.getAntiB() != null && k.getAntiD() != null) {
            tooltip.add(Component.translatable("item.bioforge.blood_slide.reactions").withStyle(ChatFormatting.DARK_GREEN));
            tooltip.add(Component.translatable("item.bioforge.blood_slide.anti_a", k.getAntiA() ? "+" : "-").withStyle(k.getAntiA() ? ChatFormatting.RED : ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.bioforge.blood_slide.anti_b", k.getAntiB() ? "+" : "-").withStyle(k.getAntiB() ? ChatFormatting.RED : ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.bioforge.blood_slide.anti_d", k.getAntiD() ? "+" : "-").withStyle(k.getAntiD() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }
}
