package net.jenkimods.bioforge.world.decalcification;

import java.util.List;

public record DecalcificationRecipe(
        String input,
        String output,
        boolean copyBloodData,
        boolean copyNbt,
        List<String> copyNbtKeys
) {}