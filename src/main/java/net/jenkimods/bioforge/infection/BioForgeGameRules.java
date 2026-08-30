package net.jenkimods.bioforge.infection;

import net.minecraft.world.level.GameRules;

public final class BioForgeGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> PERSISTENT_INFECTIONS =
            GameRules.register("bioforgePersistentInfections", GameRules.Category.MISC,
                    GameRules.BooleanValue.create(false));

    private BioForgeGameRules() {
    }

    public static void register() {
    }
}
