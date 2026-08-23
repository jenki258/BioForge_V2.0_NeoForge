package net.jenkimods.bioforge.item.infection;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.CropInfection;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.capability.CropInfectionCapability;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ContaminatedSubstrateItem extends BlockItem {

    public ContaminatedSubstrateItem() {
        super(BioForge.CONTAMINATED_SUBSTRATE.get(), new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack substrateStack = player.getItemInHand(hand);
        if (isInoculated(substrateStack)) {
            return super.use(level, player, hand);
        }

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);

        if (otherStack.getItem() instanceof SwabItem && SwabItem.isContaminated(otherStack)) {
            if (!level.isClientSide()) {
                String payload = NbtObfuscator.readString(otherStack);
                if (payload == null) return InteractionResultHolder.fail(substrateStack);

                String newPayload = replaceColonyId(payload, UUID.randomUUID().toString());

                NbtObfuscator.writeString(substrateStack, newPayload);
                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable(
                        "item.bioforge.contaminated_substrate.inoculated").withStyle(ChatFormatting.GREEN));
            }
            return InteractionResultHolder.success(substrateStack);
        }

        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.contaminated_substrate.not_inoculated").withStyle(ChatFormatting.RED));
        }
        return InteractionResultHolder.fail(substrateStack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();

        if (!isInoculated(stack)) {
            if (!level.isClientSide()) {
                context.getPlayer().sendSystemMessage(Component.translatable(
                        "item.bioforge.contaminated_substrate.not_inoculated").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }

        if (state.is(BioForgeTags.INFECTABLE_CROPS)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            LevelChunk chunk = level.getChunkAt(pos);
            var storage = CropInfectionCapability.get(chunk);
            if (storage.isInfected(pos)) {
                context.getPlayer().sendSystemMessage(Component.translatable(
                        "item.bioforge.contaminated_substrate.already_infected").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }

        if (state.is(BioForgeTags.INFECTABLE_CROPS)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            String payload = NbtObfuscator.readString(stack);
            if (payload == null || payload.equals("CLEAN")) return InteractionResult.FAIL;

            LevelChunk chunk = level.getChunkAt(pos);
            var storage = CropInfectionCapability.get(chunk);


            storage.setInfection(pos, new CropInfection(payload));
            NbtObfuscator.clear(stack);
            chunk.setUnsaved(true);

            if (!context.getPlayer().isCreative()) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
            context.getPlayer().sendSystemMessage(Component.translatable(
                    "item.bioforge.contaminated_substrate.crop_infected").withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    private int getAge(BlockState state) {
        IntegerProperty ageProp = null;
        for (var prop : state.getProperties()) {
            if (prop.getName().equals("age") && prop instanceof IntegerProperty ip) {
                ageProp = ip;
                break;
            }
        }
        if (ageProp != null) {
            return state.getValue(ageProp);
        }
        return 0;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        InteractionResult result = super.place(context);
        if (result.consumesAction() && !context.getLevel().isClientSide()) {
            if (context.getPlayer() != null && context.getPlayer().isCreative()) {
                NbtObfuscator.clear(stack);
                if (context.getPlayer() instanceof ServerPlayer sp) {
                    sp.inventoryMenu.broadcastChanges();
                }
            }
        }
        return result;
    }

    public static boolean isInoculated(ItemStack stack) {
        return NbtObfuscator.readString(stack) != null;
    }

    private static String replaceColonyId(String payload, String newColonyId) {
        return StrainData.replaceColonyId(payload, newColonyId);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!isInoculated(stack)) {
            tooltip.add(Component.translatable("item.bioforge.contaminated_substrate.tooltip.clean").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.contaminated_substrate.tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.bioforge.contaminated_substrate.tooltip.inoculated").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
