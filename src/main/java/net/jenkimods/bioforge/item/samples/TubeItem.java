package net.jenkimods.bioforge.item.samples;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledge;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledgeStore;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.item.needle.NeedleItem;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.jenkimods.bioforge.item.vaccine.VaccineItem;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.vaccine.VaccineBloodAssay;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

public class TubeItem extends Item {

    public TubeItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return net.jenkimods.bioforge.util.StackData.has(stack) && BloodSampleUtil.hasBlood(stack) ? 1 : 16;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack slide = player.getItemInHand(hand);

        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack tool = player.getItemInHand(other);

        if (level.isClientSide()) {
            return hasBlood(slide) && (tool.getItem() instanceof SyringeItem
                    || tool.getItem() instanceof VaccineItem)
                    ? InteractionResultHolder.success(slide)
                    : InteractionResultHolder.pass(slide);
        }

        if (hasBlood(slide)) {
            if (tool.getItem() instanceof VaccineItem) {
                if (!VaccineBloodAssay.createAndConsume(player, slide, tool)) {
                    player.displayClientMessage(Component.translatable(
                            "message.bioforge.vaccine.assay_invalid")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(slide);
                }
                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL,
                        SoundSource.PLAYERS, 0.8F, 1.15F);
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.vaccine.assay_created")
                        .withStyle(ChatFormatting.AQUA), true);
                return InteractionResultHolder.success(slide);
            }
            return transferBackToSyringe(level, player, slide, tool, other);
        }

        if (!(tool.getItem() instanceof SyringeItem) && !(tool.getItem() instanceof NeedleItem)) {
            if (player instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.translatable("item.bioforge.tube.need_blood"));
            return InteractionResultHolder.fail(slide);
        }

        if (!BloodSampleUtil.hasBlood(tool)) {
            if (player instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.translatable("item.bioforge.tube.need_blood"));
            return InteractionResultHolder.fail(slide);
        }

        ObfuscatedData data = BloodSampleUtil.getData(tool);
        if (data == null) return InteractionResultHolder.fail(slide);
        ItemStack filledTube;
        if (slide.getCount() > 1) {
            slide.shrink(1);
            filledTube = new ItemStack(this);
        } else {
            filledTube = slide;
        }
        BloodSampleUtil.setData(filledTube, 1,
                BloodType.fromName(data.typeName()), data.sourceName(), data.subjectUUID());

        String infectionStrain = NbtObfuscator.readInfection(tool);
        if (infectionStrain != null && !infectionStrain.isEmpty()) {
            NbtObfuscator.writeInfection(filledTube, infectionStrain);
        }

        if (tool.getItem() instanceof SyringeItem) {
            SyringeItem.consumeUse(tool);
        } else if (tool.getItem() instanceof NeedleItem) {
            NeedleItem.clearBlood(tool);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
        if (player instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.translatable("item.bioforge.tube.transferred"));
        if (filledTube != slide && !player.getInventory().add(filledTube)) {
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(),
                    player.getZ(), filledTube));
        }
        return InteractionResultHolder.success(slide);
    }

    private InteractionResultHolder<ItemStack> transferBackToSyringe(
            Level level, Player player, ItemStack tube, ItemStack syringe,
            InteractionHand syringeHand) {
        if (VaccineBloodAssay.isAssay(tube)) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.tube.assay_contaminated"));
            return InteractionResultHolder.fail(tube);
        }
        if (!(syringe.getItem() instanceof SyringeItem)) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.tube.need_syringe"));
            return InteractionResultHolder.fail(tube);
        }
        ObfuscatedData incoming = BloodSampleUtil.getData(tube);
        if (incoming == null) return InteractionResultHolder.fail(tube);
        ObfuscatedData existing = BloodSampleUtil.getData(syringe);
        BloodType incomingType = BloodType.fromName(incoming.typeName());
        if (existing != null
                && BloodType.fromName(existing.typeName()) != incomingType) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.tube.blood_mismatch"));
            return InteractionResultHolder.fail(tube);
        }
        int currentUses = existing == null ? 0 : existing.amount();
        if (currentUses >= SyringeItem.MAX_USES) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.tube.syringe_full"));
            return InteractionResultHolder.fail(tube);
        }

        String source = incoming.sourceName();
        java.util.UUID subject = incoming.subjectUUID();
        if (existing != null) {
            if (!existing.sourceName().equals(incoming.sourceName())) {
                source = existing.sourceName() + " + " + incoming.sourceName();
            }
            if (!Objects.equals(existing.subjectUUID(), incoming.subjectUUID())) subject = null;
        }
        BloodSampleUtil.setData(syringe, currentUses + 1, incomingType, source, subject);

        String incomingInfection = NbtObfuscator.readInfection(tube);
        if (incomingInfection != null && !incomingInfection.isEmpty()) {
            String existingInfection = NbtObfuscator.readInfection(syringe);
            StrainData transferred = StrainData.parse(incomingInfection);
            if (existingInfection != null && !existingInfection.isEmpty()) {
                transferred = StrainData.compete(
                        StrainData.parse(existingInfection), transferred);
            }
            NbtObfuscator.writeInfection(syringe, transferred.toPayload());
        }

        BloodSampleUtil.clear(tube);
        NbtObfuscator.clearInfection(tube);
        player.setItemInHand(syringeHand, syringe);
        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY,
                SoundSource.PLAYERS, 0.8f, 1.2f);
        player.sendSystemMessage(Component.translatable(
                "item.bioforge.tube.transferred_to_syringe"));
        return InteractionResultHolder.success(tube);
    }

    public static boolean hasBlood(ItemStack stack) {
        return BloodSampleUtil.hasBlood(stack);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!hasBlood(stack)) {
            tooltip.add(Component.translatable("item.bioforge.tube.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.tube.tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        ObfuscatedData data = BloodSampleUtil.getData(stack);
        if (data == null) return;

        VaccineBloodAssay.Data assay = VaccineBloodAssay.read(stack);
        if (assay != null) {
            tooltip.add(Component.translatable(assay.scanned()
                            ? "item.bioforge.tube.assay.scanned"
                            : "item.bioforge.tube.assay.pending")
                    .withStyle(ChatFormatting.AQUA));
            if (assay.scanned()) {
                tooltip.add(Component.translatable(
                                "item.bioforge.tube.assay.result",
                                String.format(java.util.Locale.ROOT, "%.1f%%",
                                        assay.result() * 100.0F))
                        .withStyle(ChatFormatting.WHITE));
            } else {
                tooltip.add(Component.translatable(
                                "item.bioforge.tube.assay.microscope_hint")
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            return;
        }

        tooltip.add(Component.translatable("item.bioforge.tube.filled").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.bioforge.tube.source", data.sourceName()).withStyle(ChatFormatting.WHITE));
        BloodType type = BloodType.fromName(data.typeName());
        tooltip.add(Component.translatable("item.bioforge.tube.blood_type", type.getDisplayNameComponent()).withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.bioforge.tube.tooltip.return_to_syringe")
                .withStyle(ChatFormatting.DARK_GRAY));

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
            tooltip.add(Component.translatable("item.bioforge.tube.reactions").withStyle(ChatFormatting.DARK_GREEN));
            tooltip.add(Component.translatable("item.bioforge.tube.anti_a", k.getAntiA() ? "+" : "-").withStyle(k.getAntiA() ? ChatFormatting.RED : ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.bioforge.tube.anti_b", k.getAntiB() ? "+" : "-").withStyle(k.getAntiB() ? ChatFormatting.RED : ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.bioforge.tube.anti_d", k.getAntiD() ? "+" : "-").withStyle(k.getAntiD() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }
}
