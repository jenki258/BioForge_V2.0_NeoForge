package net.jenkimods.bioforge.world.microscope;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class MicroscopeMenu extends AbstractContainerMenu {

    private final MicroscopeBlockEntity blockEntity;

    public MicroscopeMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, getBlockEntityFromBuf(playerInventory, buf));
    }

    public MicroscopeMenu(int id, Inventory playerInventory, MicroscopeBlockEntity blockEntity) {
        super(BioForge.MICROSCOPE_MENU.get(), id);
        this.blockEntity = blockEntity;

        addSlot(new SlotItemHandler(blockEntity.getItemHandler(), 0, 80, 35));

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    private static MicroscopeBlockEntity getBlockEntityFromBuf(Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof MicroscopeBlockEntity microscope) {
            return microscope;
        }
        throw new IllegalStateException("Microscope block entity not found at " + pos);
    }

    public MicroscopeBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return blockEntity.handleButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copied = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return copied;

        ItemStack source = slot.getItem();
        copied = source.copy();

        if (index == 0) {
            if (!moveItemStackTo(source, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, source);
        return copied;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(blockEntity.getLevel(),
                blockEntity.getBlockPos()), player, BioForge.MICROSCOPE.get());
    }
}
