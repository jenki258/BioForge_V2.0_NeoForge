package net.jenkimods.bioforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.jenkimods.bioforge.BioForge;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = BioForge.MODID, value = Dist.CLIENT)
public class BioForgeKeyBindings {
    public static final KeyMapping REFLEX_STRIKE = new KeyMapping(
            "key.bioforge.reflex_strike",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.bioforge"
    );

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(REFLEX_STRIKE);
    }
}
