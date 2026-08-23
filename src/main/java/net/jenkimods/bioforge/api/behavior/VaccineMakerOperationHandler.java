package net.jenkimods.bioforge.api.behavior;

import net.minecraft.world.item.ItemStack;

public interface VaccineMakerOperationHandler {
    default boolean additionalRequirements(VaccineMakerOperationContext context) { return true; }
    ItemStack createOutput(VaccineMakerOperationContext context);
}
