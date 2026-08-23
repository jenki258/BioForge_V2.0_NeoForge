package net.jenkimods.bioforge.world.laboratory;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.block.LaboratoryProcessorBlock;
import net.jenkimods.bioforge.infection.spread.ItemStrainData;
import net.jenkimods.bioforge.registry.BioForgeSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LaboratoryProcessorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_MACHINE_SLOTS = 8;
    private final ItemStackHandler items = new ItemStackHandler(MAX_MACHINE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot >= 0 && slot < station().inputSlots();
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public void deserializeNBT(net.minecraft.core.HolderLookup.Provider registries, CompoundTag nbt) {
            CompoundTag fixedSize = nbt.copy();
            fixedSize.putInt("Size", MAX_MACHINE_SLOTS);
            super.deserializeNBT(registries, fixedSize);
        }
    };
    private final int[] progress = new int[MAX_MACHINE_SLOTS];
    private final int[] maxProgress = new int[MAX_MACHINE_SLOTS];
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            int slot = index / 2;
            if (slot < 0 || slot >= MAX_MACHINE_SLOTS) return 0;
            return (index & 1) == 0 ? progress[slot] : maxProgress[slot];
        }
        @Override public void set(int index, int value) {
            int slot = index / 2;
            if (slot < 0 || slot >= MAX_MACHINE_SLOTS) return;
            if ((index & 1) == 0) progress[slot] = value;
            else maxProgress[slot] = value;
        }
        @Override public int getCount() { return MAX_MACHINE_SLOTS * 2; }
    };

    public LaboratoryProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.LABORATORY_PROCESSOR_BE.get(), pos, state);
    }

    public LaboratoryStation station() {
        return getBlockState().getBlock() instanceof LaboratoryProcessorBlock block
                ? block.station() : LaboratoryStation.CHEMICAL_SYNTHESIZER;
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            LaboratoryProcessorBlockEntity processor) {
        if (level.isClientSide()) return;
        if (processor.station().processesInPlace()) processor.tickSterilization();
        else processor.tickCrafting();
    }

    private void tickCrafting() {
        LaboratoryStation station = station();
        var recipe = LaboratoryProcessRecipeManager.INSTANCE.find(station, items);
        if (recipe.isEmpty() || !canAccept(station.resultSlot(), recipe.get().result())
                || !canAccept(station.wasteSlot(), recipe.get().waste())) {
            resetProgress(0);
            return;
        }
        LaboratoryProcessRecipe active = recipe.get();
        maxProgress[0] = active.processingTime();
        progress[0]++;
        if (progress[0] < maxProgress[0]) {
            setChanged();
            return;
        }
        active.consume(items);
        insertOutput(station.resultSlot(), active.result());
        insertOutput(station.wasteSlot(), active.waste());
        playCompletionSound(station);
        progress[0] = 0;
        setChanged();
    }

    private void tickSterilization() {
        boolean changed = false;
        boolean completed = false;
        for (int slot = 0; slot < LaboratoryStation.STERILIZATION_CHAMBER.inputSlots(); slot++) {
            ItemStack input = items.getStackInSlot(slot);
            var recipe = LaboratoryProcessRecipeManager.INSTANCE.findSingle(
                    LaboratoryStation.STERILIZATION_CHAMBER, input);
            boolean contaminated = ItemStrainData.read(input) != null;
            if (input.isEmpty() || (recipe.isEmpty() && !contaminated)) {
                if (progress[slot] != 0) {
                    progress[slot] = 0;
                    changed = true;
                }
                continue;
            }
            maxProgress[slot] = recipe.map(LaboratoryProcessRecipe::processingTime)
                    .orElse(100);
            progress[slot]++;
            changed = true;
            if (progress[slot] < maxProgress[slot]) continue;

            ItemStack output;
            if (recipe.isPresent()) {
                LaboratoryProcessRecipe active = recipe.get();
                output = active.result().copy();
                output.setCount(Math.min(output.getMaxStackSize(),
                        input.getCount() * Math.max(1, active.result().getCount())));
                if (active.copyNbt() && net.jenkimods.bioforge.util.StackData.has(input)) net.jenkimods.bioforge.util.StackData.set(output, net.jenkimods.bioforge.util.StackData.copy(input).copy());
            } else {
                output = input.copy();
            }
            ItemStrainData.clear(output);
            items.setStackInSlot(slot, output);
            progress[slot] = 0;
            completed = true;
        }
        if (completed) playCompletionSound(LaboratoryStation.STERILIZATION_CHAMBER);
        if (changed) setChanged();
    }

    private void playCompletionSound(LaboratoryStation station) {
        if (level == null) return;
        SoundEvent sound = switch (station) {
            case BARREL_PRESS -> BioForgeSounds.LIQUID_POUR.get();
            case CHEMICAL_SYNTHESIZER, PHARMA_MIXER ->
                    BioForgeSounds.CHEMICALS_COMPLETE.get();
            case STERILIZATION_CHAMBER -> BioForgeSounds.DISINFECTING.get();
        };
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    private boolean canAccept(int slot, ItemStack result) {
        if (slot < 0 || result.isEmpty()) return true;
        ItemStack output = items.getStackInSlot(slot);
        if (output.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void insertOutput(int slot, ItemStack result) {
        if (slot < 0 || result.isEmpty()) return;
        ItemStack output = items.getStackInSlot(slot);
        if (output.isEmpty()) items.setStackInSlot(slot, result.copy());
        else output.grow(result.getCount());
    }

    private void resetProgress(int slot) {
        if (progress[slot] == 0) return;
        progress[slot] = 0;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.bioforge." + station().getSerializedName());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LaboratoryProcessorMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", items.serializeNBT(registries));
        tag.putInt("InventoryVersion", 2);
        tag.putIntArray("Progress", progress);
        tag.putIntArray("MaxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.getInt("InventoryVersion") < 2) migrateLegacyInventory();
        int[] savedProgress = tag.getIntArray("Progress");
        int[] savedMaxProgress = tag.getIntArray("MaxProgress");
        if (savedProgress.length == 0 && tag.contains("Progress")) {
            progress[0] = tag.getInt("Progress");
            maxProgress[0] = Math.max(1, tag.getInt("MaxProgress"));
        } else {
            for (int slot = 0; slot < MAX_MACHINE_SLOTS; slot++) {
                progress[slot] = slot < savedProgress.length ? savedProgress[slot] : 0;
                maxProgress[slot] = slot < savedMaxProgress.length
                        ? Math.max(1, savedMaxProgress[slot]) : 160;
            }
        }
    }

    private void migrateLegacyInventory() {
        if (station() == LaboratoryStation.PHARMA_MIXER
                && items.getStackInSlot(5).isEmpty()
                && !items.getStackInSlot(4).isEmpty()) {
            items.setStackInSlot(5, items.getStackInSlot(4));
            items.setStackInSlot(4, ItemStack.EMPTY);
        } else if (station() == LaboratoryStation.CHEMICAL_SYNTHESIZER
                && items.getStackInSlot(3).isEmpty()
                && !items.getStackInSlot(4).isEmpty()) {
            items.setStackInSlot(3, items.getStackInSlot(4));
            items.setStackInSlot(4, ItemStack.EMPTY);
        }
    }

    public ItemStackHandler getItemHandler() {
        return items;
    }

    public void dropContents() {
        if (level == null) return;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D,
                    items.getStackInSlot(slot));
        }
    }
}
