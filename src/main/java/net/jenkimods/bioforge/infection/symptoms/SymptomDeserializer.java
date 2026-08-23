package net.jenkimods.bioforge.infection.symptoms;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface SymptomDeserializer {
    @Nullable
    Object resolve(String keyId, CompoundTag tag);
}
