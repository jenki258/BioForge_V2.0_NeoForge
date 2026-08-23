package net.jenkimods.bioforge.world.centrifuge;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CentrifugeMenu extends AbstractContainerMenu {

    private static final int MACHINE_SLOT_COUNT = 8;
    private static final int[] SLOT_X = {80, 52, 108, 117, 42, 52, 80, 108};
    private static final int[] SLOT_Y = {12, 23, 23, 50, 50, 77, 87, 77};
    private final CentrifugeBlockEntity blockEntity;
    private final Level level;
    private final ContainerData data;

    public CentrifugeMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory,
                (CentrifugeBlockEntity) playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new net.minecraft.world.inventory.SimpleContainerData(16));
    }

    public CentrifugeMenu(int id, Inventory playerInventory, CentrifugeBlockEntity blockEntity, ContainerData data) {
        super(BioForge.CENTRIFUGE_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.level = playerInventory.player.level();
        this.data = data;

        addDataSlots(data);
        addBlockEntitySlots(blockEntity);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    private void addBlockEntitySlots(CentrifugeBlockEntity blockEntity) {
        for (int index = 0; index < MACHINE_SLOT_COUNT; index++) {
            addSlot(new SlotItemHandler(blockEntity.getItemHandler(), index,
                    SLOT_X[index], SLOT_Y[index]));
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 111 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 169));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copied = ItemStack.EMPTY;
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return copied;

        ItemStack sourceStack = sourceSlot.getItem();
        copied = sourceStack.copy();

        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, MACHINE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(sourceStack, 0, MACHINE_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(player, sourceStack);
        return copied;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                player, BioForge.CENTRIFUGE.get());
    }

    public int getScaledProgress32() {
        float maxRatio = 0.0f;
        for (int slot = 0; slot < MACHINE_SLOT_COUNT; slot++) {
            int progress = data.get(slot * 2);
            int maxProgress = data.get(slot * 2 + 1);
            if (maxProgress <= 0 || progress <= 0) continue;
            maxRatio = Math.max(maxRatio, (float) progress / (float) maxProgress);
        }
        return (int) (maxRatio * 32.0f);
    }
}
