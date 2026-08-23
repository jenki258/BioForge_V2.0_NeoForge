package net.jenkimods.bioforge.world.centrifuge;

import java.util.List;

public record CentrifugeRecipe(
        CentrifugeIngredient input,
        CentrifugeIngredient output,
        List<CentrifugeOutput> outputs,
        boolean copyBloodData,
        boolean copyNbt,
        List<String> copyNbtKeys,
        boolean copyInfection,
        int processingTime
) {

    public CentrifugeRecipe(CentrifugeIngredient input, CentrifugeIngredient output,
                            boolean copyBloodData, boolean copyNbt, List<String> copyNbtKeys,
                            int processingTime) {
        this(input, output, List.of(), copyBloodData, copyNbt, copyNbtKeys, false, processingTime);
    }
}