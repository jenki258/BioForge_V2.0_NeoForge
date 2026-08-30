package net.jenkimods.bioforge.world.centrifuge;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.registry.BioForgeSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CentrifugeBlockEntity extends BlockEntity implements MenuProvider {

    private static final int SLOT_COUNT = 8;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }
    };

    private final int[] progress = new int[SLOT_COUNT];
    private final int[] maxProgress = new int[SLOT_COUNT];
    private boolean processing;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            int slot = index / 2;
            if (slot < 0 || slot >= SLOT_COUNT) return 0;
            return (index % 2 == 0) ? progress[slot] : maxProgress[slot];
        }

        @Override public void set(int index, int value) {
            int slot = index / 2;
            if (slot < 0 || slot >= SLOT_COUNT) return;
            if (index % 2 == 0) progress[slot] = value;
            else maxProgress[slot] = value;
        }

        @Override public int getCount() {
            return SLOT_COUNT * 2;
        }
    };

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.CENTRIFUGE_BE.get(), pos, state);
    }

    private void syncToClient() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(
                worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity be) {
        if (level.isClientSide()) return;

        boolean changed = false;
        boolean processingNow = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack input = be.items.getStackInSlot(slot);
            var recipeOpt = CentrifugeRecipeManager.INSTANCE.getRecipe(level, input);
            if (recipeOpt.isEmpty()) {
                if (be.progress[slot] != 0) {
                    be.progress[slot] = 0;
                    changed = true;
                }
                continue;
            }

            CentrifugeRecipe recipe = recipeOpt.get();
            if (recipe.copyBloodData() && !BloodSampleUtil.hasBlood(input)) {
                if (be.progress[slot] != 0) {
                    be.progress[slot] = 0;
                    changed = true;
                }
                continue;
            }

            processingNow = true;
            be.maxProgress[slot] = BioForgeServerConfig
                    .centrifugeProcessingTime(recipe.processingTime());
            be.progress[slot]++;
            if (be.progress[slot] < be.maxProgress[slot]) {
                changed = true;
                continue;
            }

            be.progress[slot] = 0;

            CentrifugeIngredient chosenIngredient;
            if (!recipe.outputs().isEmpty()) {
                int totalWeight = recipe.outputs().stream().mapToInt(CentrifugeOutput::weight).sum();
                int roll = level.random.nextInt(totalWeight);
                int cumulative = 0;
                CentrifugeOutput selected = recipe.outputs().get(0);
                for (CentrifugeOutput out : recipe.outputs()) {
                    cumulative += out.weight();
                    if (roll < cumulative) {
                        selected = out;
                        break;
                    }
                }
                chosenIngredient = selected.ingredient();
            } else {
                chosenIngredient = recipe.output();
            }

            Item outputItem = chosenIngredient.resolveItem(level.random);
            if (outputItem == null) {
                changed = true;
                continue;
            }

            ItemStack output = new ItemStack(outputItem, input.getCount());

            if (recipe.copyBloodData() && BloodSampleUtil.hasBlood(input)) {
                BloodSampleUtil.copy(input, output);
            }

            if (recipe.copyInfection()) {
                String inf = NbtObfuscator.readInfection(input);
                if (inf != null && !inf.isEmpty()) {
                    NbtObfuscator.writeInfection(output, inf);
                }
            }

            if (recipe.copyNbt() && net.jenkimods.bioforge.util.StackData.has(input)) {
                net.jenkimods.bioforge.util.StackData.set(output, net.jenkimods.bioforge.util.StackData.copy(input).copy());
            } else if (!recipe.copyNbtKeys().isEmpty() && net.jenkimods.bioforge.util.StackData.has(input)) {
                CompoundTag outTag = net.jenkimods.bioforge.util.StackData.copy(output);
                CompoundTag inTag = net.jenkimods.bioforge.util.StackData.copy(input);
                for (String key : recipe.copyNbtKeys()) {
                    if (inTag.contains(key)) {
                        outTag.put(key, inTag.get(key).copy());
                    }
                }
                net.jenkimods.bioforge.util.StackData.set(output, outTag);
            }

            be.items.setStackInSlot(slot, output);
            changed = true;
        }

        if (be.processing != processingNow) {
            if (processingNow) {
                level.playSound(null, pos, BioForgeSounds.CENTRIFUGE.get(),
                        SoundSource.BLOCKS, 0.75F, 1.0F);
            }
            be.processing = processingNow;
            changed = true;
            be.syncToClient();
        }

        if (changed) {
            be.setChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.bioforge.centrifuge");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CentrifugeMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", items.serializeNBT(registries));
        tag.putIntArray("Progress", progress);
        tag.putIntArray("MaxProgress", maxProgress);
        tag.putBoolean("Processing", processing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Inventory"));
        int[] savedProgress = tag.getIntArray("Progress");
        int[] savedMaxProgress = tag.getIntArray("MaxProgress");
        processing = tag.getBoolean("Processing");
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            progress[slot] = slot < savedProgress.length ? savedProgress[slot] : 0;
            int saved = slot < savedMaxProgress.length ? savedMaxProgress[slot] : 100;
            maxProgress[slot] = Math.max(1, saved);
        }
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    public void drops() {
        if (level == null) return;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D,
                    stack
            );
        }
    }

    public ItemStackHandler getItemHandler() {
        return items;
    }

    public boolean isProcessing() {
        return processing;
    }
}
