package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.capability.CropInfectionCapability;
import net.jenkimods.bioforge.item.infection.SwabItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public class CropInfectionSwabHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        BlockHitResult hit = event.getHitVec();
        if (hit == null) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BioForgeTags.INFECTABLE_CROPS)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!(held.getItem() instanceof SwabItem) || SwabItem.isContaminated(held)) return;

        var chunk = level.getChunkAt(pos);
        var storage = CropInfectionCapability.get(chunk);
        {
            var infection = storage.getInfection(pos);
            if (infection != null) {
                NbtObfuscator.writeString(held, infection.getStrainData());
                player.setItemInHand(event.getHand(), held);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable(
                        "item.bioforge.swab.collected_infected_crop").withStyle(ChatFormatting.GREEN));
                event.setCanceled(true);
            }
        }
    }
}
