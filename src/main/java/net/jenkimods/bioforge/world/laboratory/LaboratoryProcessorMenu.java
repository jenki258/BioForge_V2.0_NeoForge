package net.jenkimods.bioforge.world.laboratory;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class LaboratoryProcessorMenu extends AbstractContainerMenu {
    private final LaboratoryProcessorBlockEntity blockEntity;
    private final Block block;
    private final ContainerData data;
    private final LaboratoryStation station;
    private final int machineSlots;

    public LaboratoryProcessorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, findBlockEntity(inventory, buffer),
                new SimpleContainerData(LaboratoryProcessorBlockEntity.MAX_MACHINE_SLOTS * 2));
    }

    private static LaboratoryProcessorBlockEntity findBlockEntity(Inventory inventory,
                                                                   FriendlyByteBuf buffer) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(buffer.readBlockPos());
        if (blockEntity instanceof LaboratoryProcessorBlockEntity processor) return processor;
        throw new IllegalStateException("Laboratory Processor block entity is missing");
    }

    public LaboratoryProcessorMenu(int id, Inventory inventory,
                                   LaboratoryProcessorBlockEntity blockEntity, ContainerData data) {
        super(BioForge.LABORATORY_PROCESSOR_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.block = blockEntity.getBlockState().getBlock();
        this.data = data;
        this.station = blockEntity.station();
        this.machineSlots = station.machineSlots();
        addDataSlots(data);
        for (int slot = 0; slot < machineSlots; slot++) {
            int[] position = slotPosition(station, slot);
            if (slot < station.inputSlots()) {
                addSlot(new SlotItemHandler(blockEntity.getItemHandler(), slot,
                        position[0], position[1]));
            } else {
                addSlot(new SlotItemHandler(blockEntity.getItemHandler(), slot,
                        position[0], position[1]) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, station.inputSlots(), false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                blockEntity.getLevel(), blockEntity.getBlockPos()), player, block);
    }

    public int scaledProgress(int width) {
        float ratio = 0.0F;
        int slotsToCheck = station.processesInPlace() ? station.inputSlots() : 1;
        for (int slot = 0; slot < slotsToCheck; slot++) {
            int progress = data.get(slot * 2);
            int max = data.get(slot * 2 + 1);
            if (max > 0 && progress > 0) {
                ratio = Math.max(ratio, (float) progress / (float) max);
            }
        }
        return Math.min(width, Math.round(width * ratio));
    }

    public LaboratoryStation station() {
        return station;
    }

    private static int[] slotPosition(LaboratoryStation station, int slot) {
        return switch (station) {
            case BARREL_PRESS -> slot < 4
                    ? new int[]{26 + slot * 18, 35}
                    : new int[]{125, 35};
            case CHEMICAL_SYNTHESIZER -> slot < 3
                    ? new int[]{35 + slot * 18, 35}
                    : new int[]{125, 35};
            case PHARMA_MIXER -> {
                if (slot < 5) yield new int[]{17 + slot * 18, 35};
                yield slot == station.resultSlot()
                        ? new int[]{126, 26} : new int[]{126, 49};
            }
            case STERILIZATION_CHAMBER -> new int[]{
                    43 + (slot % 4) * 18, 25 + (slot / 4) * 18};
        };
    }
}
