package net.jenkimods.bioforge.world.incubator;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class IncubatorMenu extends AbstractContainerMenu {

    private final IncubatorBlockEntity blockEntity;
    private final ContainerData data;

    public IncubatorMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, getBlockEntity(inv, buf), new SimpleContainerData(2));
    }

    public IncubatorMenu(int id, Inventory inv, IncubatorBlockEntity be, ContainerData data) {
        super(BioForge.INCUBATOR_MENU.get(), id);
        this.blockEntity = be;
        this.data = data;
        addDataSlots(data);

        addSlot(new SlotItemHandler(be.getItemHandler(), 0, 80, 7));
        addSlot(new SlotItemHandler(be.getItemHandler(), 1, 57, 52));
        addSlot(new SlotItemHandler(be.getItemHandler(), 2, 80, 52));
        addSlot(new SlotItemHandler(be.getItemHandler(), 3, 103, 52));

        for (int row = 0; row < 3; ++row)
            for (int col = 0; col < 9; ++col)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; ++col)
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
    }

    private static IncubatorBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = inv.player.level().getBlockEntity(pos);
        if (be instanceof IncubatorBlockEntity incubator) return incubator;
        throw new IllegalStateException("Incubator not found at " + pos);
    }

    public int getProgress() { return data.get(0); }
    public int getMaxProgress() { return data.get(1); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();

        if (index < 4) {
            if (!moveItemStackTo(source, 4, 40, true)) {
                return ItemStack.EMPTY;
            }
        }
        else {
            if (slotIsValidForSlot(source, 0)) {
                if (!moveItemStackTo(source, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            else if (slotIsValidForSlot(source, 1)) {
                if (!moveItemStackTo(source, 1, 4, false)) {
                    return ItemStack.EMPTY;
                }
            }
            else if (index >= 4 && index < 31) {
                if (!moveItemStackTo(source, 31, 40, false)) {
                    return ItemStack.EMPTY;
                }
            }
            else if (index >= 31 && index < 40) {
                if (!moveItemStackTo(source, 4, 31, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        slot.onTake(player, source);
        return copy;
    }

    private boolean slotIsValidForSlot(ItemStack stack, int machineSlot) {
        SlotItemHandler slot = (SlotItemHandler) slots.get(machineSlot);
        return slot.getItemHandler().isItemValid(machineSlot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, BioForge.INCUBATOR.get());
    }
}
