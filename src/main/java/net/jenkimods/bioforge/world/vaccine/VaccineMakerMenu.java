package net.jenkimods.bioforge.world.vaccine;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.vaccine.VaccineMakerPageDefinition;
import net.jenkimods.bioforge.api.vaccine.VaccineMakerPageRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class VaccineMakerMenu extends AbstractContainerMenu {
    public static final int PAGE_BUTTON_BASE = 128;
    public static final int MAX_PAGE_COUNT = 64;
    public static final int CORRECTION_PAGE_BUTTON_BASE = 300;
    public static final int MAX_CORRECTION_PAGE_COUNT = 64;
    public static final int EXTENSION_BUTTON_BASE = 1000;

    private final VaccineMakerBlockEntity blockEntity;
    private final ContainerData data;
    private final DataSlot activePage = DataSlot.standalone();
    private final DataSlot correctionPage = DataSlot.standalone();
    private final List<VaccineMakerPageDefinition> pages;
    private final List<PageSlot> machineSlotViews = new ArrayList<>();
    private int machineViewCount;

    public VaccineMakerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, getBlockEntity(inventory, buffer), new SimpleContainerData(4));
    }

    public VaccineMakerMenu(int id, Inventory inventory, VaccineMakerBlockEntity blockEntity,
                            ContainerData data) {
        super(BioForge.VACCINE_MAKER_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.data = data;
        VaccineMakerPageRegistry.bootstrapBuiltIns();
        this.pages = VaccineMakerPageRegistry.pages();
        if (pages.isEmpty() || pages.size() > MAX_PAGE_COUNT) {
            throw new IllegalStateException(
                    "Vaccine Maker requires 1.." + MAX_PAGE_COUNT + " registered pages");
        }
        activePage.set(Math.max(0, Math.min(
                pages.size() - 1, blockEntity.getSelectedPageIndex())));
        correctionPage.set(Math.max(0, Math.min(
                MAX_CORRECTION_PAGE_COUNT - 1,
                blockEntity.getSelectedCorrectionPage())));
        addDataSlots(data);
        addDataSlot(activePage);
        addDataSlot(correctionPage);

        IItemHandler handler = blockEntity.getItemHandler();
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            final int viewPage = pageIndex;
            pages.get(pageIndex).slots().entrySet().stream()
                    .sorted(Comparator.comparingInt(
                            (java.util.Map.Entry<Integer,
                                    VaccineMakerPageDefinition.SlotPosition> entry) ->
                                    entry.getKey()))
                    .forEach(entry -> {
                        VaccineMakerPageDefinition.SlotPosition position = entry.getValue();
                        PageSlot slot = new PageSlot(
                                handler, entry.getKey(), position.x(), position.y(),
                                viewPage, entry.getKey() == VaccineMakerBlockEntity.OUTPUT_SLOT);
                        machineSlotViews.add(slot);
                        addSlot(slot);
                    });
        }
        machineViewCount = slots.size();

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        43 + column * 18, 132 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 43 + column * 18, 190));
        }
    }

    private static VaccineMakerBlockEntity getBlockEntity(
            Inventory inventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof VaccineMakerBlockEntity maker) return maker;
        throw new IllegalStateException("Vaccine Maker not found at " + pos);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= PAGE_BUTTON_BASE && id < PAGE_BUTTON_BASE + MAX_PAGE_COUNT) {
            int requestedPage = id - PAGE_BUTTON_BASE;
            if (requestedPage < 0 || requestedPage >= pages.size()) return false;
            activePage.set(requestedPage);
            blockEntity.setSelectedPageIndex(requestedPage);
            return true;
        }
        if (id >= CORRECTION_PAGE_BUTTON_BASE
                && id < CORRECTION_PAGE_BUTTON_BASE + MAX_CORRECTION_PAGE_COUNT) {
            int requestedPage = id - CORRECTION_PAGE_BUTTON_BASE;
            correctionPage.set(requestedPage);
            blockEntity.setSelectedCorrectionPage(requestedPage);
            return true;
        }
        if (id >= EXTENSION_BUTTON_BASE) {
            return getActivePage().handleButton(this, player, id);
        }
        return blockEntity.handleButton(player, id);
    }

    public void selectPageLocally(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < pages.size()) activePage.set(pageIndex);
    }

    public void selectCorrectionPageLocally(int pageIndex) {
        correctionPage.set(Math.max(0, Math.min(
                MAX_CORRECTION_PAGE_COUNT - 1, pageIndex)));
    }

    public int getCorrectionPage() {
        return Math.max(0, Math.min(
                MAX_CORRECTION_PAGE_COUNT - 1, correctionPage.get()));
    }

    public int getActivePageIndex() {
        return Math.max(0, Math.min(pages.size() - 1, activePage.get()));
    }

    public VaccineMakerPageDefinition getActivePage() {
        return pages.get(getActivePageIndex());
    }

    public ResourceLocation getActivePageId() {
        return getActivePage().id();
    }

    public List<VaccineMakerPageDefinition> getPages() {
        return pages;
    }

    public List<Slot> getActiveMachineSlots() {
        return machineSlotViews.stream().filter(Slot::isActive)
                .map(slot -> (Slot) slot).toList();
    }

    public Optional<Slot> getActiveMachineSlot(int logicalSlot) {
        return machineSlotViews.stream()
                .filter(PageSlot::isActive)
                .filter(slot -> slot.logicalSlot() == logicalSlot)
                .map(slot -> (Slot) slot)
                .findFirst();
    }

    public ItemStack getMachineStack(int logicalSlot) {
        return machineSlotViews.stream()
                .filter(slot -> slot.logicalSlot() == logicalSlot)
                .map(Slot::getItem)
                .findFirst().orElse(ItemStack.EMPTY);
    }

    public VaccineMakerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    public float getQuality() {
        return data.get(2) / 1000.0f;
    }

    public int getStatus() {
        return data.get(3);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < machineViewCount
                && !slots.get(slotId).isActive()) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.isActive() || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < machineViewCount) {
            if (!moveItemStackTo(source, machineViewCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int target = 0; target < machineViewCount; target++) {
                Slot targetSlot = slots.get(target);
                if (targetSlot.isActive() && targetSlot.mayPlace(source)
                        && moveItemStackTo(source, target, target + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, BioForge.VACCINE_MAKER.get());
    }

    private final class PageSlot extends SlotItemHandler {
        private final int logicalSlot;
        private final int pageIndex;
        private final boolean output;

        private PageSlot(IItemHandler handler, int logicalSlot, int x, int y,
                         int pageIndex, boolean output) {
            super(handler, logicalSlot, x, y);
            this.logicalSlot = logicalSlot;
            this.pageIndex = pageIndex;
            this.output = output;
        }

        private int logicalSlot() {
            return logicalSlot;
        }

        @Override
        public boolean isActive() {
            return getActivePageIndex() == pageIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && !output && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && super.mayPickup(player);
        }
    }
}
