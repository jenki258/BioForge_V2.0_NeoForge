package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.spread.ItemStrainData;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public class InfectedFoodHandler {

    @SubscribeEvent
    public static void onEatFinish(LivingEntityUseItemEvent.Finish event) {
        ItemStack item = event.getItem();
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        StrainData strain = ItemStrainData.read(item);
        if (strain == null) return;
        boolean drink = item.getUseAnimation() == UseAnim.DRINK;
        boolean foodRoute = !drink
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.FOOD_BORNE)
                && net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(strain, InfectionType.FOOD_BORNE);
        boolean animalRoute = !drink
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.ANIMALS)
                && net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(strain, InfectionType.ANIMALS);
        boolean waterRoute = drink
                && BioForgeServerConfig.isTransmissionEnabled(InfectionType.WATER_BORNE)
                && net.jenkimods.bioforge.definition.BioForgeDefinitionManager
                .hasTransmissionBehavior(strain, InfectionType.WATER_BORNE);
        if (!foodRoute && !animalRoute && !waterRoute) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data == null) return;
        float chance = waterRoute ? BioForgeServerConfig.waterExposureChance()
                : BioForgeServerConfig.foodExposureChance();
        float strength = strain.getSymptom("infection_strength")
                .map(InfectedFoodHandler::parseStrength).orElse(0.5F);
        if (entity.getRandom().nextFloat() < Math.min(1.0F, chance * (0.5F + strength)
                * InfectionLifecycleRegistry.INSTANCE.infectivity(strain))) {
            strain.applyToEntity(data, entity);
        }
    }

    private static float parseStrength(String raw) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return 0.5F;
        }
    }
}
