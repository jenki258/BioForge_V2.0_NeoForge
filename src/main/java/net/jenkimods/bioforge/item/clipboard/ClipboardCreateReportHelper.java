package net.jenkimods.bioforge.item.clipboard;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.naming.StrainNamingManager;
import net.jenkimods.bioforge.vaccine.MedicalReportStrainBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ClipboardCreateReportHelper {

    public static void createReport(CompoundTag tag, Player player, ItemStack paper) {
        if (player == null) return;

        if (tag.isEmpty()) return;
        if (!paper.is(Items.PAPER)) return;

        ItemStack report = new ItemStack(BioForge.MEDICAL_REPORT.get());
        net.jenkimods.bioforge.util.StackData.set(report, tag.copy());
        if (tag.hasUUID("SubjectUUID") && player.level() instanceof ServerLevel server
                && server.getEntity(tag.getUUID("SubjectUUID")) instanceof LivingEntity subject) {
            MedicalReportStrainBinding.capture(report, subject);
        }
        String fingerprint = MedicalReportStrainBinding.fingerprint(report);
        if (player instanceof net.minecraft.server.level.ServerPlayer researcher
                && fingerprint != null && MedicalReportItem.hasCompleteRecord(tag)) {
            StrainNamingManager.discoverResearch(researcher, fingerprint);
        }

        if (!player.getAbilities().instabuild) {
            paper.shrink(1);
        }
        if (!player.getInventory().add(report)) {
            player.drop(report, false);
        }
    }
}
