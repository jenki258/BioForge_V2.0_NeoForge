package net.jenkimods.bioforge.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.jenkimods.bioforge.BioForge;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = BioForge.MODID)
public class LoadedChunksTracker {
    private static final Set<LevelChunk> loadedChunks = Collections.synchronizedSet(new HashSet<>());

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk chunk) {
            loadedChunks.add(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk chunk) {
            loadedChunks.remove(chunk);
        }
    }

    public static Set<LevelChunk> getLoadedChunks() {
        return loadedChunks;
    }
}