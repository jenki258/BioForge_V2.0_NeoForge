package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;


public final class MedicalReportStrainBinding {
    private static final String CHANNEL = "medical_report_strain";
    private static final int VERSION = 1;

    private MedicalReportStrainBinding() {}

    public static void capture(ItemStack document, LivingEntity subject) {
        if (document == null || document.isEmpty() || subject == null) return;
        InfectionData infection = InfectionCapability.get(subject);
        if (infection == null || !infection.isInfected()
                || infection.getPathogenId() == null) {
            NbtObfuscator.clear(document, CHANNEL);
            return;
        }
        StrainData strain = StrainData.buildFrom(infection);
        bind(document, strain.toPayload());
    }

    public static void bind(ItemStack document, String strainPayload) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putString("Fingerprint", StrainFingerprint.ofPayload(strainPayload));
        NbtObfuscator.writeCompound(document, CHANNEL, data);
    }

    public static boolean matchesSample(ItemStack document, String strainPayload) {
        String fingerprint = fingerprint(document);
        return fingerprint != null
                && fingerprint.equals(StrainFingerprint.ofPayload(strainPayload));
    }

    @Nullable
    public static String fingerprint(ItemStack document) {
        if (document == null || document.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(document)) return null;
        CompoundTag data = NbtObfuscator.readCompound(document, CHANNEL);
        if (data == null || data.getInt("Version") != VERSION) return null;
        String value = data.getString("Fingerprint");
        return value.isBlank() ? null : value;
    }
}
