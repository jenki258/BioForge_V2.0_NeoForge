package net.jenkimods.bioforge.api.vaccine;

import net.jenkimods.bioforge.world.vaccine.VaccineMakerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;








public record VaccineMakerPageDefinition(
        ResourceLocation id,
        int order,
        Supplier<Component> title,
        Supplier<ItemStack> icon,
        Map<Integer, SlotPosition> slots,
        @Nullable ButtonHandler buttonHandler
) {
    public static final int MACHINE_SLOT_COUNT = 21;

    public record SlotPosition(int x, int y) {}

    @FunctionalInterface
    public interface ButtonHandler {
        boolean handle(VaccineMakerMenu menu, Player player, int buttonId);
    }

    public VaccineMakerPageDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(slots, "slots");
        LinkedHashMap<Integer, SlotPosition> checked = new LinkedHashMap<>();
        slots.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            int slot = entry.getKey();
            if (slot < 0 || slot >= MACHINE_SLOT_COUNT) {
                throw new IllegalArgumentException("Vaccine Maker slot out of range: " + slot);
            }
            checked.put(slot, Objects.requireNonNull(entry.getValue(), "slot position"));
        });
        slots = Map.copyOf(checked);
    }

    public boolean handleButton(VaccineMakerMenu menu, Player player, int buttonId) {
        return buttonHandler != null && buttonHandler.handle(menu, player, buttonId);
    }

    public static Builder builder(ResourceLocation id, Supplier<Component> title,
                                  Supplier<ItemStack> icon) {
        return new Builder(id, title, icon);
    }


    public static final class Builder {
        private final ResourceLocation id;
        private final Supplier<Component> title;
        private final Supplier<ItemStack> icon;
        private final LinkedHashMap<Integer, SlotPosition> slots = new LinkedHashMap<>();
        private int order;
        private ButtonHandler buttonHandler;

        private Builder(ResourceLocation id, Supplier<Component> title,
                        Supplier<ItemStack> icon) {
            this.id = Objects.requireNonNull(id, "id");
            this.title = Objects.requireNonNull(title, "title");
            this.icon = Objects.requireNonNull(icon, "icon");
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder slot(int logicalSlot, int x, int y) {
            if (slots.putIfAbsent(logicalSlot, new SlotPosition(x, y)) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Vaccine Maker slot in page: " + logicalSlot);
            }
            return this;
        }

        public Builder buttonHandler(ButtonHandler buttonHandler) {
            this.buttonHandler = Objects.requireNonNull(buttonHandler, "buttonHandler");
            return this;
        }

        public VaccineMakerPageDefinition build() {
            return new VaccineMakerPageDefinition(
                    id, order, title, icon, slots, buttonHandler);
        }
    }
}
