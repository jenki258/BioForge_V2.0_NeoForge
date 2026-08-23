package net.jenkimods.bioforge.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

public final class StackData {
    private StackData() {
    }

    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    public static CompoundTag copy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> updater) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        }
    }

    public static void clear(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
    }
}
