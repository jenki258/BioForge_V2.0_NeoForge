package net.jenkimods.bioforge.api.behavior;

import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.infection.InfectionData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

@FunctionalInterface
public interface SymptomBehavior {
    void tick(ServerLevel level, LivingEntity entity, InfectionData infection,
              SymptomDefinition definition, Object value);
}
