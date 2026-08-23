package net.jenkimods.bioforge.api.behavior;

import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.minecraft.world.entity.LivingEntity;

public record MutationEffectContext(MutationDefinition.Effect effect,
                                    MutationDefinition mutation,
                                    InfectionData infection,
                                    LivingEntity entity,
                                    int effectIndex) {}
