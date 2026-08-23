package net.jenkimods.bioforge.item.stethoscope;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class StethoscopeSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, BioForge.MODID);

    public static final Supplier<SoundEvent> HEARTBEAT_NORMAL =
            SOUNDS.register("stethoscope.heartbeat.normal",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BioForge.MODID, "stethoscope.heartbeat.normal")));

    public static final Supplier<SoundEvent> HEARTBEAT_FAST =
            SOUNDS.register("stethoscope.heartbeat.fast",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BioForge.MODID, "stethoscope.heartbeat.fast")));

    public static final Supplier<SoundEvent> HEARTBEAT_SLOW =
            SOUNDS.register("stethoscope.heartbeat.slow",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BioForge.MODID, "stethoscope.heartbeat.slow")));

    public static final Supplier<SoundEvent> LUNGS_NORMAL =
            SOUNDS.register("stethoscope.lungs.normal",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BioForge.MODID, "stethoscope.lungs.normal")));

    public static final Supplier<SoundEvent> LUNGS_CRACKLE =
            SOUNDS.register("stethoscope.lungs.crackle",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BioForge.MODID, "stethoscope.lungs.crackle")));
}
