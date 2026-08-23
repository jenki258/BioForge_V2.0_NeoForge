package net.jenkimods.bioforge.item.infection;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.block.MicrobialMatBlock;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ColonyCoreBlockEntity extends BlockEntity {

    private String strainData = null;
    public UUID colonyId = null;
    public PathogenType pathogen = null;
    public Set<InfectionType> infectionTypes = EnumSet.noneOf(InfectionType.class);
    private int resources = 25;
    private int infectedBlockCount = 0;
    private int colonyRadius = 20;
    private int maxInfestedBlocks = 100;
    private static final int MAT_SPAWN_COST = 1;
    private static final int MAX_RESOURCES = 5000;

    public ColonyCoreBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.COLONY_CORE_BE.get(), pos, state);
    }

    public void setStrainData(String encrypted) {
        this.strainData = encrypted;
        if (encrypted != null && !encrypted.equals("CLEAN")) {
            StrainData strain = StrainData.parse(encrypted);
            this.colonyId = strain.getColonyId().orElse(null);
            this.pathogen = strain.getPathogen();
            this.infectionTypes.clear();
            this.infectionTypes.addAll(strain.getInfectionTypes());
            strain.getSymptom("ColonyRadius").ifPresent(val -> {
                try { colonyRadius = Math.round(Float.parseFloat(val)); } catch (Exception ignored) {}
            });
            strain.getSymptom("MaxInfestedBlocks").ifPresent(val -> {
                try { maxInfestedBlocks = Math.round(Float.parseFloat(val)); } catch (Exception ignored) {}
            });
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String getStrainData() { return strainData; }

    public void addResources(int amount) {
        resources = Math.min(resources + amount, MAX_RESOURCES);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean consumeResources(int amount) {
        if (resources >= amount) {
            resources -= amount;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return true;
        }
        return false;
    }

    public int getResources() { return resources; }
    public int getColonyRadius() { return colonyRadius; }
    public int getMaxInfestedBlocks() { return maxInfestedBlocks; }

    public boolean canCreateInfestedBlock() { return infectedBlockCount < maxInfestedBlocks; }
    public void incrementInfestedCount() { infectedBlockCount++; setChanged(); }
    public void decrementInfestedCount() { infectedBlockCount--; setChanged(); }

    public void randomTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (strainData == null) return;
        int attempts = 1 + random.nextInt(3);
        for (int i = 0; i < attempts; i++) {
            if (!consumeResources(MAT_SPAWN_COST)) break;
            int dx = random.nextInt(7) - 3;
            int dz = random.nextInt(7) - 3;
            int dy = random.nextFloat() < 0.2f ? 1 : 0;
            BlockPos target = pos.offset(dx, dy, dz);
            if (target.equals(pos)) continue;
            if (!level.getBlockState(target).isAir()) continue;
            BlockPos below = target.below();
            if (!isValidSubstrate(level.getBlockState(below))) continue;
            level.setBlock(target, BioForge.MICROBIAL_MAT.get().defaultBlockState()
                    .setValue(MicrobialMatBlock.GROWTH, 0)
                    .setValue(MicrobialMatBlock.HOST_CROP, false), 3);
            if (level.getBlockEntity(target) instanceof MicrobialMatBlockEntity mat) {
                mat.setStrainData(strainData);
                mat.setCorePos(pos);
            }
        }
    }

    private boolean isValidSubstrate(BlockState state) {
        return state.is(BlockTags.create(ResourceLocation.tryBuild("bioforge", "substrate/organic")));
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (strainData != null) NbtObfuscator.writeString(tag, strainData);
        tag.putInt("Resources", resources);
        tag.putInt("InfectedCount", infectedBlockCount);
        tag.putInt("ColonyRadius", colonyRadius);
        tag.putInt("MaxInfestedBlocks", maxInfestedBlocks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (NbtObfuscator.hasData(tag)) {
            String data = NbtObfuscator.readString(tag);
            if (data != null) setStrainData(data);
        }
        resources = tag.getInt("Resources");
        infectedBlockCount = tag.getInt("InfectedCount");
        colonyRadius = tag.getInt("ColonyRadius");
        maxInfestedBlocks = tag.getInt("MaxInfestedBlocks");
    }

    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { CompoundTag t = super.getUpdateTag(registries); saveAdditional(t, registries); return t; }
    @Override public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) { super.handleUpdateTag(tag, registries); loadAdditional(tag, registries); }
    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) { if (pkt.getTag() != null) handleUpdateTag(pkt.getTag(), registries); }
}
