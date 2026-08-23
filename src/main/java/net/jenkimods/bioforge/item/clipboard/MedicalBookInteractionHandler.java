package net.jenkimods.bioforge.item.clipboard;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.item.crispr.CrisprNotesItem;
import net.jenkimods.bioforge.vaccine.VaccineResearchNotes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public final class MedicalBookInteractionHandler {

    private MedicalBookInteractionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBook(PlayerInteractEvent.RightClickItem event) {
        ItemStack target = event.getItemStack();
        boolean bookTarget = target.is(Items.WRITABLE_BOOK);
        boolean paperTarget = target.is(Items.PAPER);
        if (!bookTarget && !paperTarget) return;

        Player player = event.getEntity();
        InteractionHand sourceHand = event.getHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack source = player.getItemInHand(sourceHand);
        if (!(source.getItem() instanceof ClipboardItem)
                && !(source.getItem() instanceof MedicalReportItem)
                && !(source.getItem() instanceof CrisprNotesItem)) {
            return;
        }

        CompoundTag sourceTag = net.jenkimods.bioforge.util.StackData.copy(source);
        VaccineResearchNotes.Data research = VaccineResearchNotes.read(source);
        if (sourceTag == null || (!sourceTag.hasUUID("SessionId") && research == null)) return;

        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
        event.setCanceled(true);

        if (!player.level().isClientSide()) {
            if (paperTarget) {
                if (research != null) {
                    ItemStack copy = source.copy();
                    copy.setCount(1);
                    if (!player.getAbilities().instabuild) target.shrink(1);
                    if (!player.getInventory().add(copy)) player.drop(copy, false);
                } else {
                    ClipboardCreateReportHelper.createReport(sourceTag, player, target);
                }
            } else {
                if (research != null) {
                    VaccineResearchNotes.appendToBook(target, research);
                }
                if (sourceTag.hasUUID("SessionId")) {
                    ClipboardAppendToBookHelper.appendToBook(sourceTag, player, target);
                }
                player.setItemInHand(event.getHand(), target);
            }
        }
    }
}
