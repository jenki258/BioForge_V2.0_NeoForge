package net.jenkimods.bioforge.world.centrifuge;

public record CentrifugeOutput(CentrifugeIngredient ingredient, int weight) {
    public CentrifugeOutput(CentrifugeIngredient ingredient) {
        this(ingredient, 1);
    }
}