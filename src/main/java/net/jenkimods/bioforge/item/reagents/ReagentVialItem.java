package net.jenkimods.bioforge.item.reagents;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledgeStore;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.item.bones.BoneMarrowItem;
import net.jenkimods.bioforge.item.bones.WitheredBoneMarrowItem;
import net.jenkimods.bioforge.item.clipboard.ClipboardHelper;
import net.jenkimods.bioforge.item.needle.NeedleItem;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.jenkimods.bioforge.world.data.ReagentType;
import net.jenkimods.bioforge.util.NbtObfuscator.ObfuscatedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ReagentVialItem extends Item {

    public enum Type { ANTI_A, ANTI_B, ANTI_D }

    private static final String KEY_USED     = "VialUsed";
    private static final String KEY_REACTED  = "VialReacted";
    private static final String KEY_CATEGORY = "VialBloodCategory";

    public final Type type;

    public ReagentVialItem(Type type) {
        super(new Item.Properties().stacksTo(64));
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack vialStack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.pass(vialStack);

        if (isUsed(vialStack)) return InteractionResultHolder.fail(vialStack);

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);

        if (!isAllowedSampleItem(otherStack)) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.reagent_vial.invalid_sample").withStyle(ChatFormatting.DARK_GRAY));
            return InteractionResultHolder.fail(vialStack);
        }

        if (!BloodSampleUtil.hasBlood(otherStack)) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.reagent_vial.no_sample").withStyle(ChatFormatting.DARK_GRAY));
            return InteractionResultHolder.fail(vialStack);
        }

        ObfuscatedData needleData = BloodSampleUtil.getData(otherStack);
        if (needleData == null) return InteractionResultHolder.fail(vialStack);

        BloodType bloodType = BloodType.fromName(needleData.typeName());
        boolean   isAnimal  = bloodType.getCategory() == BloodType.Category.NON_HUMAN;
        boolean   reacted   = !isAnimal && checkReaction(bloodType);

        ItemStack usedVial = vialStack.copy();
        usedVial.setCount(1);
        vialStack.shrink(1);

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(usedVial);
        tag.putBoolean(KEY_USED,     true);
        tag.putBoolean(KEY_REACTED,  reacted);
        tag.putString (KEY_CATEGORY, bloodType.getCategory().name());
        net.jenkimods.bioforge.util.StackData.set(usedVial, tag);

        if (player instanceof ServerPlayer sp && needleData.subjectUUID() != null) {
            MinecraftServer server = sp.getServer();
            if (server != null) {
                UUID   subjectUUID = needleData.subjectUUID();
                String sourceName  = needleData.sourceName();
                boolean isSubjectPlayer = server.getPlayerList().getPlayer(subjectUUID) != null;

                String subjectType = isSubjectPlayer
                        ? "player"
                        : level.getEntities(null,
                                sp.getBoundingBox().inflate(64))
                        .stream()
                        .filter(e -> e.getUUID().equals(subjectUUID))
                        .findFirst()
                        .map(e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                .getKey(e.getType()).toString())
                        .orElse("unknown");

                BloodKnowledgeStore.get(server).recordReagent(
                        sp.getUUID(),
                        subjectUUID,
                        sourceName,
                        subjectType,
                        isSubjectPlayer,
                        bloodType,
                        toReagentType(this.type),
                        reacted
                );

                ClipboardHelper.recordBloodData(
                        bloodType.name(),
                        type == Type.ANTI_A ? reacted : null,
                        type == Type.ANTI_B ? reacted : null,
                        type == Type.ANTI_D ? reacted : null,
                        player,
                        needleData.subjectUUID()
                );
            }
        }

        if (otherStack.getItem() instanceof SyringeItem) {
            SyringeItem.consumeUse(otherStack);
        } else if (otherStack.getItem() instanceof BoneMarrowItem || otherStack.getItem() instanceof WitheredBoneMarrowItem) {
            otherStack.shrink(1);
        } else {
            BloodSampleUtil.clear(otherStack);
        }

        if (vialStack.isEmpty()) {
            player.setItemInHand(hand, usedVial);
        } else {
            if (!player.getInventory().add(usedVial)) {
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), usedVial));
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0f, 1.2f);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static ReagentType toReagentType(Type t) {
        return switch (t) {
            case ANTI_A -> ReagentType.ANTI_A;
            case ANTI_B -> ReagentType.ANTI_B;
            case ANTI_D -> ReagentType.ANTI_D;
        };
    }

    private static boolean isAllowedSampleItem(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof NeedleItem
                || item instanceof SyringeItem
                || item instanceof BoneMarrowItem
                || item instanceof WitheredBoneMarrowItem;
    }

    private boolean checkReaction(BloodType type) {
        String name = type.name();
        return switch (this.type) {
            case ANTI_A -> name.startsWith("A_") || name.startsWith("AB_");
            case ANTI_B -> name.startsWith("B_") || name.startsWith("AB_");
            case ANTI_D -> name.endsWith("_POSITIVE");
        };
    }

    public static float getReactedPredicate(ItemStack stack) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        if (!tag.getBoolean(KEY_USED))   return 0.0f;
        if (tag.getBoolean(KEY_REACTED)) return 1.0f;
        return 2.0f;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        if (!tag.getBoolean(KEY_USED)) {
            tooltip.add(Component.translatable(getReagentDescKey()).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("item.bioforge.reagent_vial.tooltip.instructions")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if ("NON_HUMAN".equals(tag.getString(KEY_CATEGORY))) {
            tooltip.add(Component.translatable("item.bioforge.reagent_vial.tooltip.animal")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.bioforge.reagent_vial.tooltip.used")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (tag.getBoolean(KEY_REACTED)) {
            tooltip.add(Component.translatable("item.bioforge.reagent_vial.tooltip.positive")
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("item.bioforge.reagent_vial.tooltip.used")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.bioforge.reagent_vial.tooltip.negative")
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.bioforge.reagent_vial.tooltip.used")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean isUsed(ItemStack stack) {
        return net.jenkimods.bioforge.util.StackData.copy(stack).getBoolean(KEY_USED);
    }

    public static boolean hasReaction(ItemStack stack) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        return tag.getBoolean(KEY_USED) && tag.getBoolean(KEY_REACTED);
    }

    private String getReagentDescKey() {
        return switch (type) {
            case ANTI_A -> "item.bioforge.reagent_vial.anti_a.desc";
            case ANTI_B -> "item.bioforge.reagent_vial.anti_b.desc";
            case ANTI_D -> "item.bioforge.reagent_vial.anti_d.desc";
        };
    }
}
