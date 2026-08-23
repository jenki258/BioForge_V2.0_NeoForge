package net.jenkimods.bioforge.infection.naming;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StrainNameClientHandler {
    private StrainNameClientHandler() {}

    public static void open(String fingerprint) {
        Minecraft.getInstance().setScreen(new StrainNamingScreen(fingerprint));
    }
}
