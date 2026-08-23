package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.CropInfection;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.capability.CropInfectionCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

@EventBusSubscriber(modid = BioForge.MODID)
public class CropInfectionSpreadHandler {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!BioForgeServerConfig.isTransmissionEnabled(InfectionType.FOOD_BORNE)
                && !BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL)) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        ServerLevel level = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (level == null) return;

        int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        double chance = (randomTickSpeed * 2.0) / 4096.0;

        RandomSource rand = level.random;

        for (LevelChunk chunk : LoadedChunksTracker.getLoadedChunks()) {
            if (chunk.getLevel() != level) continue;
            var storage = CropInfectionCapability.get(chunk);
            {
                List<Map.Entry<BlockPos, CropInfection>> entries = new ArrayList<>(storage.getAllInfections().entrySet());
                for (Map.Entry<BlockPos, CropInfection> entry : entries) {
                    if (!supportsCropSpread(entry.getValue())) continue;
                    BlockPos pos = entry.getKey();
                    BlockState state = chunk.getBlockState(pos);
                    if (!isMature(state) || !state.is(BioForgeTags.INFECTABLE_CROPS)) continue;

                    for (int attempt = 0; attempt < 3; attempt++) {
                        if (rand.nextFloat() >= chance) continue;

                        BlockPos neighbor = pos.offset(rand.nextInt(3) - 1, 0, rand.nextInt(3) - 1);
                        if (neighbor.equals(pos)) continue;

                        LevelChunk neighborChunk = level.getChunkAt(neighbor);
                        BlockState neighborState = neighborChunk.getBlockState(neighbor);
                        if (!isMature(neighborState) || !neighborState.is(BioForgeTags.INFECTABLE_CROPS)) continue;

                        var neighborStorage = CropInfectionCapability.get(neighborChunk);
                        if (neighborStorage.isInfected(neighbor)) continue;

                        CropInfection newInfection = new CropInfection(entry.getValue().getStrainData());
                        neighborStorage.setInfection(neighbor, newInfection);
                        neighborChunk.setUnsaved(true);
                        break;
                    }
                }
            }
        }
    }

    private static boolean isMature(BlockState state) {
        for (var prop : state.getProperties()) {
            if (prop.getName().equals("age") && prop instanceof IntegerProperty ageProp) {
                int max = ageProp.getPossibleValues().stream().max(Integer::compare).orElse(7);
                return state.getValue(ageProp) == max;
            }
        }
        return false;
    }

    private static boolean supportsCropSpread(CropInfection infection) {
        String raw = infection.getStrainData();
        if (raw == null) return false;
        int separator = raw.indexOf('|');
        StrainData strain = StrainData.parse(separator < 0 ? raw : raw.substring(separator + 1));
        return BioForgeServerConfig.isTransmissionEnabled(InfectionType.FOOD_BORNE)
                && BioForgeDefinitionManager.hasTransmissionBehavior(strain, InfectionType.FOOD_BORNE)
                || BioForgeServerConfig.isTransmissionEnabled(InfectionType.ENVIRONMENTAL)
                && BioForgeDefinitionManager.hasTransmissionBehavior(strain, InfectionType.ENVIRONMENTAL);
    }
}
