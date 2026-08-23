package net.jenkimods.bioforge.item.crispr;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.crispr.CrisprGuideProfile;
import net.jenkimods.bioforge.crispr.CrisprAssayDefinition;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.item.infection.PetriDishBlockEntity;
import net.jenkimods.bioforge.item.infection.PetriDishItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CrisprCartridgeItem extends Item {
    public static final String DEFAULT_SEQUENCE = "NNNN";
    private static final String SEQUENCE_TAG = "CrisprSequence";
    private static final String SLOT_TAG = "CrisprTargetSlot";
    private static final String PROFILE_TAG = "CrisprGuideProfile";
    private static final String ASSAY_SCORE_TAG = "CrisprAssayScore";
    private static final String CHANNEL = "crispr_cartridge";

    public CrisprCartridgeItem() {
        super(new Properties().stacksTo(1));
    }

    public static String getSequence(ItemStack stack) {
        String sequence = data(stack).getString(SEQUENCE_TAG)
                .toUpperCase(java.util.Locale.ROOT);
        return sequence.length() == 4 ? sequence : DEFAULT_SEQUENCE;
    }

    public static void setSequence(ItemStack stack, String sequence) {
        if (sequence == null || sequence.length() != 4) return;
        CompoundTag data = data(stack);
        data.putString(SEQUENCE_TAG, sequence.toUpperCase(java.util.Locale.ROOT));
        data.remove(ASSAY_SCORE_TAG);
        writeData(stack, data);
    }

    public static void cycleBase(ItemStack stack, int baseIndex, int direction, String alphabet) {
        if (baseIndex < 0 || baseIndex >= 4) return;
        String allowed = "N" + alphabet.toUpperCase(java.util.Locale.ROOT);
        char[] bases = getSequence(stack).toCharArray();
        int current = allowed.indexOf(bases[baseIndex]);
        if (current < 0) current = 0;
        bases[baseIndex] = allowed.charAt(
                Math.floorMod(current + Integer.signum(direction), allowed.length()));
        setSequence(stack, new String(bases));
    }

    public static void assign(ItemStack stack, int cartridgeIndex, ResourceLocation profile) {
        CompoundTag tag = data(stack);
        boolean assignmentChanged = !tag.contains(SLOT_TAG)
                || tag.getInt(SLOT_TAG) != cartridgeIndex
                || !profile.toString().equals(tag.getString(PROFILE_TAG));
        tag.putInt(SLOT_TAG, cartridgeIndex);
        tag.putString(PROFILE_TAG, profile.toString());
        if (assignmentChanged) tag.remove(ASSAY_SCORE_TAG);
        writeData(stack, tag);
    }

    public static int getAssignedSlot(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag.contains(SLOT_TAG) ? tag.getInt(SLOT_TAG) : -1;
    }

    @Nullable
    public static ResourceLocation getAssignedProfile(ItemStack stack) {
        return ResourceLocation.tryParse(data(stack).getString(PROFILE_TAG));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = context.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof PetriDishBlockEntity dish) || !dish.isInoculated()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        return runAssay(context.getItemInHand(), dish.getStrainData(), dish.growthStage,
                () -> dish.consumeGrowth(1), context.getPlayer(), (ServerLevel) context.getLevel(), pos);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack cartridge = player.getItemInHand(hand);
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack dish = player.getItemInHand(other);
        if (!(dish.getItem() instanceof PetriDishItem)
                || !PetriDishItem.isInoculated(dish)) {
            return InteractionResultHolder.pass(cartridge);
        }
        if (level.isClientSide()) return InteractionResultHolder.success(cartridge);
        String payload = NbtObfuscator.readString(dish);
        int growth = net.jenkimods.bioforge.util.StackData.copy(dish).getInt("Growth");
        InteractionResult result = runAssay(cartridge, payload, growth, () -> {
            int currentGrowth = net.jenkimods.bioforge.util.StackData.copy(dish).getInt("Growth");
            net.jenkimods.bioforge.util.StackData.update(dish,
                    tag -> tag.putInt("Growth", Math.max(0, currentGrowth - 1)));
        }, player, (ServerLevel) level, player.blockPosition());
        return result.consumesAction()
                ? InteractionResultHolder.success(cartridge)
                : InteractionResultHolder.fail(cartridge);
    }

    private static InteractionResult runAssay(ItemStack cartridge, @Nullable String payload,
                                              int growth, Runnable cultureCost,
                                              @Nullable Player player, ServerLevel level,
                                              BlockPos pos) {
        int slot = getAssignedSlot(cartridge);
        if (payload == null || slot < 0 || slot >= CrisprGuideProfile.CARTRIDGE_COUNT) {
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.crispr.assay_unassigned").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        CrisprAssayDefinition assay = BioForgeResearchData.assay(
                ResourceLocation.tryBuild(BioForge.MODID, "default"))
                .orElse(new CrisprAssayDefinition(
                        ResourceLocation.tryBuild(BioForge.MODID, "default"), 1, 1, true));
        if (growth < assay.minimumGrowth()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.crispr.assay_no_growth").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        ResourceLocation profileId = getAssignedProfile(cartridge);
        CrisprGuideProfile profile = profileId == null ? null
                : BioForgeResearchData.guideProfile(profileId).orElse(null);
        if (profile == null) {
            profile = BioForgeResearchData.guideProfile(
                    ResourceLocation.tryBuild(BioForge.MODID, "default")).orElse(null);
        }
        if (profile == null) return InteractionResult.FAIL;
        String expected = profile.expectedCartridge(StrainData.parse(payload), slot);
        String actual = getSequence(cartridge);
        int score = 0;
        for (int index = 0; index < Math.min(expected.length(), actual.length()); index++) {
            if (expected.charAt(index) == actual.charAt(index)) score++;
        }
        CompoundTag cartridgeData = data(cartridge);
        cartridgeData.putInt(ASSAY_SCORE_TAG, score);
        writeData(cartridge, cartridgeData);
        for (int cost = 0; cost < assay.cultureCost(); cost++) cultureCost.run();

        if (player != null) {
            player.displayClientMessage((assay.showNumericScore()
                    ? Component.translatable(
                    "message.bioforge.crispr.assay_result", score, 4)
                    : Component.translatable(
                    "message.bioforge.crispr.assay_result_hidden"))
                    .withStyle(score == 4 ? ChatFormatting.GREEN
                            : score >= 2 ? ChatFormatting.GOLD : ChatFormatting.RED), true);
        }
        level.sendParticles(score == 4 ? ParticleTypes.HAPPY_VILLAGER
                        : score >= 2 ? ParticleTypes.ENCHANT : ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5,
                4 + score * 2, 0.18, 0.08, 0.18, 0.02);
        level.playSound(null, pos, score == 4 ? SoundEvents.AMETHYST_BLOCK_CHIME
                        : SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                0.7f, Mth.lerp(score / 4.0f, 0.65f, 1.35f));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.crispr_cartridge.sequence",
                getSequence(stack)).withStyle(ChatFormatting.AQUA));
        int slot = getAssignedSlot(stack);
        if (slot >= 0) {
            tooltip.add(Component.translatable("item.bioforge.crispr_cartridge.assignment",
                    slot / 5 + 1, slot % 5 + 1).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.bioforge.crispr_cartridge.unassigned")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        CompoundTag data = data(stack);
        if (data.contains(ASSAY_SCORE_TAG)) {
            tooltip.add(Component.translatable("item.bioforge.crispr_cartridge.assay",
                    data.getInt(ASSAY_SCORE_TAG), 4)
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("item.bioforge.crispr_cartridge.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static CompoundTag data(ItemStack stack) {
        if (!net.jenkimods.bioforge.util.StackData.has(stack)) return new CompoundTag();
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        CompoundTag hidden = NbtObfuscator.readCompound(root, CHANNEL);
        if (hidden != null) return hidden;
        CompoundTag legacy = new CompoundTag();
        if (root.contains(SEQUENCE_TAG)) legacy.putString(SEQUENCE_TAG, root.getString(SEQUENCE_TAG));
        if (root.contains(SLOT_TAG)) legacy.putInt(SLOT_TAG, root.getInt(SLOT_TAG));
        if (root.contains(PROFILE_TAG)) legacy.putString(PROFILE_TAG, root.getString(PROFILE_TAG));
        if (root.contains(ASSAY_SCORE_TAG)) {
            legacy.putInt(ASSAY_SCORE_TAG, root.getInt(ASSAY_SCORE_TAG));
        }
        return legacy;
    }

    private static void writeData(ItemStack stack, CompoundTag data) {
        net.jenkimods.bioforge.util.StackData.update(stack, root -> {
            root.remove(SEQUENCE_TAG);
            root.remove(SLOT_TAG);
            root.remove(PROFILE_TAG);
            root.remove(ASSAY_SCORE_TAG);
        });
        NbtObfuscator.writeCompound(stack, CHANNEL, data);
    }
}
