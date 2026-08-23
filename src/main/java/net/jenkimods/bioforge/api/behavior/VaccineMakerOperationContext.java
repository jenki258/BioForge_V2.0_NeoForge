package net.jenkimods.bioforge.api.behavior;

import net.jenkimods.bioforge.world.vaccine.VaccineMakerBlockEntity;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerRecipe;

public record VaccineMakerOperationContext(VaccineMakerBlockEntity machine,
                                           VaccineMakerRecipe recipe,
                                           float quality) {}
