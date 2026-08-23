package net.jenkimods.bioforge.compat;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class CuriosCompat {
    private static final ConcurrentHashMap<Class<?>, Method> EQUIPPED_METHODS =
            new ConcurrentHashMap<>();
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();
    private static final Method GET_INVENTORY = findInventoryMethod();

    private CuriosCompat() {}

    public static boolean anyEquipped(LivingEntity entity,
                                      Predicate<ItemStack> predicate) {
        IItemHandler equipped = equippedInventory(entity);
        if (equipped == null) return false;
        for (int slot = 0; slot < equipped.getSlots(); slot++) {
            ItemStack stack = equipped.getStackInSlot(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return true;
        }
        return false;
    }

    private static IItemHandler equippedInventory(LivingEntity entity) {
        if (GET_INVENTORY == null || entity == null) return null;
        try {
            Object optionalInventory = GET_INVENTORY.invoke(null, entity);
            Object inventory = unwrap(optionalInventory);
            if (inventory == null) return null;
            Method equippedMethod = EQUIPPED_METHODS.computeIfAbsent(
                    inventory.getClass(), type -> {
                        try {
                            return type.getMethod("getEquippedCurios");
                        } catch (ReflectiveOperationException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
            Object equipped = equippedMethod.invoke(inventory);
            return equipped instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportFailure(exception);
            return null;
        }
    }

    private static Object unwrap(Object optional) {
        if (optional instanceof Optional<?> javaOptional) {
            return javaOptional.orElse(null);
        }
        if (optional != null) {
            try {
                Object resolved = optional.getClass().getMethod("resolve").invoke(optional);
                if (resolved instanceof Optional<?> resolvedOptional) {
                    return resolvedOptional.orElse(null);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return optional;
    }

    private static Method findInventoryMethod() {
        if (!ModList.get().isLoaded("curios")) return null;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            return api.getMethod("getCuriosInventory", LivingEntity.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            reportFailure(exception);
            return null;
        }
    }

    private static void reportFailure(Throwable throwable) {
        if (REPORTED_FAILURE.compareAndSet(false, true)) {
            BioForge.LOGGER.warn(
                    "Curios is installed, but BioForge could not inspect equipped Curios items: {}",
                    throwable.getMessage());
        }
    }
}
