package net.jenkimods.bioforge.infection.capability;

import net.jenkimods.bioforge.infection.CropInfection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

public class CropInfectionStorage implements ICropInfectionStorage {
    private final Map<BlockPos, CropInfection> infections = new HashMap<>();
    @Nullable private final LevelChunk owner;

    public CropInfectionStorage() {
        this(null);
    }

    public CropInfectionStorage(@Nullable LevelChunk owner) {
        this.owner = owner;
    }

    @Override
    public void setInfection(BlockPos pos, CropInfection infection) {
        infections.put(pos.immutable(), infection);
        onChanged();
    }

    @Override
    public void removeInfection(BlockPos pos) {
        infections.remove(pos);
        onChanged();
    }

    @Override
    public CropInfection getInfection(BlockPos pos) {
        return infections.get(pos);
    }

    @Override
    public boolean isInfected(BlockPos pos) {
        return infections.containsKey(pos);
    }

    @Override
    public Map<BlockPos, CropInfection> getAllInfections() {
        return infections;
    }

    private void onChanged() {
        if (owner != null) owner.setUnsaved(true);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag map = new CompoundTag();
        for (Map.Entry<BlockPos, CropInfection> entry : infections.entrySet()) {
            map.put(String.valueOf(entry.getKey().asLong()), entry.getValue().writeToNBT());
        }
        tag.put("infections", map);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        infections.clear();
        CompoundTag map = tag.getCompound("infections");
        for (String key : map.getAllKeys()) {
            long packed = Long.parseLong(key);
            BlockPos pos = BlockPos.of(packed);
            CropInfection infection = new CropInfection(map.getCompound(key).getString("strainData"));
            infections.put(pos, infection);
        }
    }
}
