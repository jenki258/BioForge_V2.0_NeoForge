package net.jenkimods.bioforge.api.behavior;

import net.jenkimods.bioforge.api.definition.TransmissionDefinition;
import net.jenkimods.bioforge.infection.InfectionData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

@FunctionalInterface
public interface TransmissionBehavior {
    void tick(ServerLevel level, LivingEntity host, InfectionData infection,
              ResourceLocation transmissionId, TransmissionDefinition definition);
}
