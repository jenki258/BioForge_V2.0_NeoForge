package net.jenkimods.bioforge.registry;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Objects;
import java.util.function.Supplier;

public final class BioForgeSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, BioForge.MODID);

    public static final Supplier<SoundEvent> COUGH = register("symptom.cough");
    public static final Supplier<SoundEvent> SNEEZE = register("symptom.sneeze");
    public static final Supplier<SoundEvent> PARANOIA_VOICE =
            register("symptom.paranoia_voice");
    public static final Supplier<SoundEvent> DISINFECTING =
            register("machine.disinfecting");
    public static final Supplier<SoundEvent> GENES_COMPLETE =
            register("machine.genes_complete");
    public static final Supplier<SoundEvent> EMERGENCY =
            register("machine.emergency");
    public static final Supplier<SoundEvent> TESTING_COMPLETE =
            register("machine.testing_complete");
    public static final Supplier<SoundEvent> CENTRIFUGE =
            register("machine.centrifuge");
    public static final Supplier<SoundEvent> LIQUID_POUR =
            register("machine.liquid_pour");
    public static final Supplier<SoundEvent> CHEMICALS_COMPLETE =
            register("machine.chemicals_complete");
    public static final Supplier<SoundEvent> UI_BUTTON = register("ui.button");
    public static final Supplier<SoundEvent> UI_SATISFYING =
            register("ui.satisfying");

    private BioForgeSounds() {
    }

    private static Supplier<SoundEvent> register(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                Objects.requireNonNull(ResourceLocation.tryBuild(BioForge.MODID, id))));
    }
}
