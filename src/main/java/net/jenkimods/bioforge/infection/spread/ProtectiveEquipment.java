package net.jenkimods.bioforge.infection.spread;

import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.compat.CuriosCompat;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ProtectiveEquipment {
    private ProtectiveEquipment() {}

    public static float outgoingAirMultiplier(LivingEntity entity) {
        return hasFullHazcureSuit(entity)
                || hasEquipped(entity, BioForgeTags.BLOCKS_OUTGOING_AIRBORNE) ? 0.0F : 1.0F;
    }

    public static float incomingAirMultiplier(LivingEntity entity) {
        if (hasEquipped(entity, BioForgeTags.BLOCKS_INCOMING_AIRBORNE)) return 0.0F;
        if (hasFullHazcureSuit(entity)) {
            return BioForgeServerConfig.hazcureIncomingAirMultiplier();
        }
        return hasEquipped(entity, BioForgeTags.REDUCES_INCOMING_AIRBORNE)
                ? BioForgeServerConfig.medicalMaskIncomingAirMultiplier() : 1.0F;
    }

    public static float outgoingContactMultiplier(LivingEntity entity) {
        return hasFullHazcureSuit(entity)
                || hasEquipped(entity, BioForgeTags.BLOCKS_OUTGOING_CONTACT) ? 0.0F : 1.0F;
    }

    public static float incomingContactMultiplier(LivingEntity entity) {
        if (hasEquipped(entity, BioForgeTags.BLOCKS_INCOMING_CONTACT)) return 0.0F;
        return hasFullHazcureSuit(entity)
                ? BioForgeServerConfig.hazcureIncomingContactMultiplier() : 1.0F;
    }

    public static boolean blocksSyringes(LivingEntity entity) {
        return hasFullHazcureSuit(entity) || hasEquipped(entity, BioForgeTags.BLOCKS_SYRINGES);
    }

    public static boolean blocksHeatSymptoms(LivingEntity entity) {
        return hasFullHazcureSuit(entity) || hasEquipped(entity, BioForgeTags.BLOCKS_HEAT_SYMPTOMS);
    }

    public static boolean blocksChillSymptoms(LivingEntity entity) {
        return hasFullHazcureSuit(entity) || hasEquipped(entity, BioForgeTags.BLOCKS_CHILL_SYMPTOMS);
    }

    public static boolean hasEquipped(LivingEntity entity, TagKey<Item> tag) {
        for (ItemStack stack : entity.getArmorSlots()) {
            if (!stack.isEmpty() && stack.is(tag)) return true;
        }
        for (ItemStack stack : entity.getHandSlots()) {
            if (!stack.isEmpty() && stack.is(tag)) return true;
        }
        return CuriosCompat.anyEquipped(entity, stack -> stack.is(tag));
    }

    public static boolean hasFullHazcureSuit(LivingEntity entity) {
        int pieces = 0;
        for (ItemStack stack : entity.getArmorSlots()) {
            if (!stack.isEmpty() && stack.is(BioForgeTags.HAZCURE_PIECES)) pieces++;
        }
        return pieces >= 4;
    }
}
