package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.CropInfection;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.capability.CropInfectionCapability;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

@EventBusSubscriber(modid = BioForge.MODID)
public class CropInfectionDropHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        Player player = event.getPlayer();
        if (!state.is(BioForgeTags.INFECTABLE_CROPS)) return;

        Level level = (Level) event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        LevelChunk chunk = level.getChunkAt(pos);
        var storage = CropInfectionCapability.get(chunk);
        {
            if (storage.isInfected(pos)) {
                var infection = storage.getInfection(pos);
                if (infection == null) return;

                String itemStrain = stripColonyId(infection.getStrainData());
                StrainData parsed = StrainData.parse(itemStrain);
                boolean supportedRoute = BioForgeServerConfig
                        .isTransmissionEnabled(InfectionType.FOOD_BORNE)
                        && BioForgeDefinitionManager.hasTransmissionBehavior(
                        parsed, InfectionType.FOOD_BORNE)
                        || BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL)
                        && BioForgeDefinitionManager.hasTransmissionBehavior(
                        parsed, InfectionType.ENVIRONMENTAL);
                if (!supportedRoute) {
                    storage.removeInfection(pos);
                    chunk.setUnsaved(true);
                    return;
                }

                event.setCanceled(true);

                float strength = infection.getInfectionStrength();
                List<ItemStack> originalDrops = Block.getDrops(
                        state, serverLevel, pos, null, player, player.getMainHandItem());

                for (ItemStack drop : originalDrops) {
                    if (itemStrain != null && strength > 0.0f
                            && level.random.nextFloat() <= strength) {
                        NbtObfuscator.writeStringDeterministic(drop, itemStrain);
                    }
                    Block.popResource(level, pos, drop);
                }

                storage.removeInfection(pos);
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                chunk.setUnsaved(true);
            }
        }
    }

    private static String stripColonyId(String strain) {
        if (strain == null) return null;
        int firstPipe = strain.indexOf('|');
        if (firstPipe == -1) return strain;
        return strain.substring(firstPipe + 1);
    }
}
