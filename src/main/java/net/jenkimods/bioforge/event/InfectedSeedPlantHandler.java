package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.CropInfection;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.spread.ItemStrainData;
import net.jenkimods.bioforge.infection.capability.CropInfectionCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public class InfectedSeedPlantHandler {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor.isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        BlockState placedState = event.getPlacedBlock();
        if (!placedState.is(BioForgeTags.INFECTABLE_CROPS)) return;

        ItemStack stack = player.getMainHandItem();
        StrainData strain = ItemStrainData.read(stack);
        if (strain == null || !(BioForgeServerConfig.isTransmissionEnabled(InfectionType.FOOD_BORNE)
                && BioForgeDefinitionManager.hasTransmissionBehavior(strain, InfectionType.FOOD_BORNE)
                || BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL)
                && BioForgeDefinitionManager.hasTransmissionBehavior(strain, InfectionType.ENVIRONMENTAL))) return;
        String strainRaw = strain.toPayload();

        ServerLevel level = (ServerLevel) levelAccessor;
        BlockPos pos = event.getPos();
        LevelChunk chunk = level.getChunkAt(pos);
        var storage = CropInfectionCapability.get(chunk);
        {
            if (!storage.isInfected(pos)) {
                storage.setInfection(pos, new CropInfection(strainRaw));
                chunk.setUnsaved(true);
            }
        }
    }
}
