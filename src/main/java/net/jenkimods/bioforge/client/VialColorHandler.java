package net.jenkimods.bioforge.client;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.item.vaccine.VaccineItem;
import net.jenkimods.bioforge.vaccine.SymptomTabletProfile;
import net.jenkimods.bioforge.item.reagents.DiagnosticReagentItem;
import net.jenkimods.bioforge.vaccine.VaccineProfile;
import net.jenkimods.bioforge.vaccine.DirectedVaccineProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@EventBusSubscriber(modid = BioForge.MODID, value = Dist.CLIENT)
public class VialColorHandler {

    private static final int COLOR_UNUSED          = 0xFFD4C19F;
    private static final int COLOR_POSITIVE_HUMAN  = 0xFF91160D;
    private static final int COLOR_NEGATIVE_HUMAN  = 0xFFF5A7A2;
    private static final int COLOR_ANIMAL          = 0xFFFCF74B;

    private static final int COLOR_FULL_DEFAULT         = 0xFF52CFC4;
    private static final int COLOR_MUTATION_DEFAULT     = 0xFFA66AE3;
    private static final int COLOR_TRANSMISSION_DEFAULT = 0xFFE69245;
    private static final int COLOR_SYMPTOM_DEFAULT      = 0xFF66C878;
    private static final int COLOR_RANDOM_MUTATION_DEFAULT = 0xFFD43A78;

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                VialColorHandler::getVialColor,
                BioForge.ANTI_A_VIAL.get(),
                BioForge.ANTI_B_VIAL.get(),
                BioForge.ANTI_D_VIAL.get()
        );
        event.register(VialColorHandler::getDiagnosticReagentColor,
                BioForge.PATHOGEN_REAGENT.get(),
                BioForge.VISIBILITY_REAGENT.get());
        event.register(VialColorHandler::getVaccineColor,
                BioForge.VACCINE.get(),
                BioForge.MUTATION_VACCINE.get(),
                BioForge.TRANSMISSION_VACCINE.get(),
                BioForge.SYMPTOM_VACCINE.get(),
                BioForge.RANDOM_MUTATION_VACCINE.get());
        event.register(VialColorHandler::getSymptomTabletColor,
                BioForge.SYMPTOM_TABLET.get());
        event.register((stack, tintIndex) -> tintIndex == 1 ? 0xFF7A193D : 0xFFFFFFFF,
                BioForge.WINE_MUST.get());
    }

    private static int getVialColor(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) return 0xFFFFFFFF;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        boolean used    = tag.getBoolean("VialUsed");
        boolean reacted = tag.getBoolean("VialReacted");

        if (!used) return COLOR_UNUSED;

        String category = tag.getString("VialBloodCategory");
        if ("NON_HUMAN".equals(category)) return COLOR_ANIMAL;

        return reacted ? COLOR_POSITIVE_HUMAN : COLOR_NEGATIVE_HUMAN;
    }

    private static int getDiagnosticReagentColor(ItemStack stack, int tintIndex) {
        return tintIndex == 1
                ? opaque(DiagnosticReagentItem.getLiquidColor(stack)) : 0xFFFFFFFF;
    }

    private static int getVaccineColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return 0xFFFFFFFF;

        VaccineProfile profile = VaccineProfile.read(stack);
        DirectedVaccineProfile directed = DirectedVaccineProfile.read(stack);
        VaccineItem.Kind kind = visualKind(stack, directed);
        if (profile == null && directed == null) return defaultColor(kind);

        String payload = profile != null ? profile.strainPayload() : directed.strainPayload();
        int hash = canonicalVisualPayload(payload).hashCode();



        float hue = baseHue(kind) + unitByte(hash) * 0.08f - 0.04f;
        float saturation = clamp(0.62f + unitByte(hash >>> 8) * 0.20f, 0.0f, 1.0f);
        float brightness = clamp(0.72f
                + (unitByte(hash >>> 16) - 0.5f) * 0.12f, 0.0f, 1.0f);
        return hsvToRgb(hue, saturation, brightness);
    }

    private static int getSymptomTabletColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return 0xFFFFFFFF;
        SymptomTabletProfile profile = SymptomTabletProfile.read(stack);
        if (profile == null) return 0xFFD8D2C4;
        String symptom = profile.symptomId();
        if (symptom.contains("temperature_plus") || symptom.contains("fever")
                || symptom.contains("heat")) return qualityColor(0xE45B3C, profile.quality());
        if (symptom.contains("temperature_minus") || symptom.contains("cold")
                || symptom.contains("chill")) return qualityColor(0x59C7E8, profile.quality());
        if (symptom.contains("oxygen") || symptom.contains("lung")
                || symptom.contains("respir")) return qualityColor(0x4CA9D9, profile.quality());
        if (symptom.contains("heart") || symptom.contains("blood")
                || symptom.contains("perfusion")) return qualityColor(0xD94B63, profile.quality());
        if (symptom.contains("neural") || symptom.contains("reflex")) {
            return qualityColor(0x8C6DDB, profile.quality());
        }
        if (symptom.contains("lesion") || symptom.contains("redness")) {
            return qualityColor(0xB94747, profile.quality());
        }
        if (symptom.contains("secretion")) return qualityColor(0x75B85A, profile.quality());
        if (symptom.contains("swelling")) return qualityColor(0xD6A84B, profile.quality());
        if (symptom.contains("infection_strength")) {
            return qualityColor(0xA43A73, profile.quality());
        }
        int hash = symptom.hashCode();
        float hue = unitByte(hash) * 0.82F;
        float saturation = 0.55F + unitByte(hash >>> 8) * 0.25F;
        float brightness = 0.72F + profile.quality() * 0.18F;
        return hsvToRgb(hue, saturation, brightness);
    }

    private static int qualityColor(int rgb, float quality) {
        float factor = 0.72F + clamp(quality, 0.0F, 1.0F) * 0.28F;
        int red = Math.min(255, Math.round(((rgb >>> 16) & 0xFF) * factor));
        int green = Math.min(255, Math.round(((rgb >>> 8) & 0xFF) * factor));
        int blue = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static VaccineItem.Kind visualKind(ItemStack stack,
                                                DirectedVaccineProfile directed) {
        if (directed != null) {
            return switch (directed.category()) {
                case MUTATION -> VaccineItem.Kind.MUTATION;
                case TRANSMISSION -> VaccineItem.Kind.TRANSMISSION;
                case SYMPTOM -> VaccineItem.Kind.SYMPTOM;
            };
        }
        if (stack.getItem() instanceof VaccineItem vaccine) return vaccine.getKind();
        return VaccineItem.Kind.FULL;
    }

    private static int defaultColor(VaccineItem.Kind kind) {
        return switch (kind) {
            case FULL -> COLOR_FULL_DEFAULT;
            case MUTATION -> COLOR_MUTATION_DEFAULT;
            case TRANSMISSION -> COLOR_TRANSMISSION_DEFAULT;
            case SYMPTOM -> COLOR_SYMPTOM_DEFAULT;
            case RANDOM_MUTATION -> COLOR_RANDOM_MUTATION_DEFAULT;
        };
    }

    private static float baseHue(VaccineItem.Kind kind) {
        return switch (kind) {
            case FULL -> 0.49f;
            case MUTATION -> 0.76f;
            case TRANSMISSION -> 0.075f;
            case SYMPTOM -> 0.36f;
            case RANDOM_MUTATION -> 0.94f;
        };
    }

    private static float unitByte(int value) {
        return (value & 0xFF) / 255.0f;
    }


    private static String canonicalVisualPayload(String payload) {
        if (payload == null || payload.isBlank()) return "";
        String[] sections = payload.split(";");
        String[] header = sections[0].split("\\|", -1);
        String pathogen = header.length >= 3 ? header[header.length - 2]
                : header.length > 0 ? header[0] : "";
        String transmissions = header.length >= 2 ? sortedCsv(header[header.length - 1]) : "";
        List<String> genes = new ArrayList<>();
        for (int index = 1; index < sections.length; index++) {
            String gene = sections[index];
            if (gene.startsWith("mutations=")) {
                gene = "mutations=" + sortedCsv(gene.substring("mutations=".length()));
            }
            genes.add(gene);
        }
        genes.sort(String::compareTo);
        return pathogen + "|" + transmissions + ";" + String.join(";", genes);
    }

    private static String sortedCsv(String value) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.split(",");
        Arrays.sort(parts);
        return String.join(",", parts);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int hsvToRgb(float hue, float saturation, float brightness) {
        hue = hue - (float) Math.floor(hue);
        float scaled = hue * 6.0f;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float p = brightness * (1.0f - saturation);
        float q = brightness * (1.0f - saturation * fraction);
        float t = brightness * (1.0f - saturation * (1.0f - fraction));
        float red;
        float green;
        float blue;
        switch (sector % 6) {
            case 0 -> { red = brightness; green = t; blue = p; }
            case 1 -> { red = q; green = brightness; blue = p; }
            case 2 -> { red = p; green = brightness; blue = t; }
            case 3 -> { red = p; green = q; blue = brightness; }
            case 4 -> { red = t; green = p; blue = brightness; }
            default -> { red = brightness; green = p; blue = q; }
        }
        return 0xFF000000 | (Math.round(red * 255.0f) << 16)
                | (Math.round(green * 255.0f) << 8)
                | Math.round(blue * 255.0f);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }
}
