package net.jenkimods.bioforge.registry;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public final class BioForgeEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, BioForge.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> STRAIN_IMMUNITY = EFFECTS.register(
            "strain_immunity", StrainImmunityEffect::new);

    private BioForgeEffects() {}

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }

    private static final class StrainImmunityEffect extends MobEffect {
        private StrainImmunityEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x55D6C2);
        }
    }
}
