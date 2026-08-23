package net.jenkimods.bioforge.client;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.item.crispr.GeneImprintItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;


@EventBusSubscriber(modid = BioForge.MODID, value = Dist.CLIENT)
public final class GeneImprintModelHandler {
    private static final float UNKNOWN = 0.04f;
    private static final float MUTATION = 0.08f;
    private static final float TRANSMISSION = 0.12f;
    private static final float SYMPTOM_GENERIC = 0.16f;

    private static final Map<String, Float> SYMPTOM_VARIANTS = Map.ofEntries(
            Map.entry("heart_rate", 0.20f),
            Map.entry("lung_sound", 0.25f),
            Map.entry("temperature_plus", 0.30f),
            Map.entry("temperature_minus", 0.35f),
            Map.entry("otoscope_redness", 0.40f),
            Map.entry("otoscope_lesions", 0.45f),
            Map.entry("otoscope_secretion", 0.50f),
            Map.entry("otoscope_swelling", 0.55f),
            Map.entry("reflex_delay", 0.60f),
            Map.entry("reflex_strength", 0.65f),
            Map.entry("neural_damage", 0.70f),
            Map.entry("oxygen_saturation", 0.75f),
            Map.entry("perfusion_index", 0.80f),
            Map.entry("infection_strength", 0.85f),
            Map.entry("colony_radius", 0.90f),
            Map.entry("max_infested_blocks", 0.95f),
            Map.entry("microscope_visibility", 1.00f)
    );

    private GeneImprintModelHandler() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                BioForge.GENE_IMPRINT.get(),
                ResourceLocation.tryBuild(BioForge.MODID, "gene_imprint_variant"),
                (stack, level, entity, seed) -> variant(stack)));
    }

    static float variant(ItemStack stack) {
        GeneImprintItem.Data data = GeneImprintItem.read(stack);
        if (data == null) return 0.0f;

        if (!data.identified()) return UNKNOWN;
        if (data.category() == VaccineTargetCategory.MUTATION) return MUTATION;
        if (data.category() == VaccineTargetCategory.TRANSMISSION) return TRANSMISSION;
        return SYMPTOM_VARIANTS.getOrDefault(data.target(), SYMPTOM_GENERIC);
    }
}
