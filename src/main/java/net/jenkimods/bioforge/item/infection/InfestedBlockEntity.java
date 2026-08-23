package net.jenkimods.bioforge.item.infection;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.block.InfestedBlock;
import net.jenkimods.bioforge.block.MicrobialMatBlock;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InfestedBlockEntity extends BlockEntity {

    private String strainData = null;
    public PathogenType pathogen = null;
    public Set<InfectionType> infectionTypes = EnumSet.noneOf(InfectionType.class);
    public UUID colonyId = null;
    public float infectionStrength = 0.5f;
    @Nullable
    private BlockPos corePos = null;
    @Nullable
    public BlockState hostState = null;

    private int tickCounter = 0;
    private static final int RESOURCE_INTERVAL = 5;
    private static final int SPREAD_INTERVAL = 3;
    private int colonyRadius = 20;

    public InfestedBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.INFESTED_BLOCK_BE.get(), pos, state);
        if (level != null) {
            hostState = level.getBlockState(pos.below());
        }
    }

    @Nullable
    public BlockPos getCorePos() {
        return corePos;
    }

    public void setStrainData(String encrypted) {
        this.strainData = encrypted;
        if (encrypted != null && !encrypted.equals("CLEAN")) {
            StrainData strain = StrainData.parse(encrypted);
            this.colonyId = strain.getColonyId().orElse(null);
            this.pathogen = strain.getPathogen();
            this.infectionTypes.clear();
            this.infectionTypes.addAll(strain.getInfectionTypes());

            strain.getSymptom("InfectionStrength").ifPresent(val -> {
                try { infectionStrength = Float.parseFloat(val); } catch (Exception ignored) {}
            });
            strain.getSymptom("ColonyRadius").ifPresent(val -> {
                try { colonyRadius = Math.round(Float.parseFloat(val)); } catch (Exception ignored) {}
            });
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String getStrainData() { return strainData; }

    public void setCorePos(BlockPos pos) {
        this.corePos = pos.immutable();
        setChanged();
    }

    public void randomTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (strainData == null) return;

        if (!isWithinInfluence(level)) {
            die(level, pos);
            return;
        }

        tickCounter = (tickCounter + 1) % 1000;
        setChanged();

        if (tickCounter % RESOURCE_INTERVAL == 0) {
            generateResources(level);
        }

        if (tickCounter % SPREAD_INTERVAL == 0) {
            attemptUndergroundSpread(level, pos);
        }

        int growth = state.getValue(InfestedBlock.GROWTH);
        if (growth < 4 && random.nextFloat() < getGrowthChance()) {
            level.setBlock(pos, state.setValue(InfestedBlock.GROWTH, growth + 1), 3);
        }

        if (growth >= 2 && random.nextFloat() < 0.10f) {
            int dx = random.nextInt(3) - 1;
            int dz = random.nextInt(3) - 1;
            int dy = random.nextFloat() < 0.3f ? 1 : 0;
            BlockPos target = pos.offset(dx, dy, dz);
            if (level.getBlockState(target).isAir()) {
                BlockPos below = target.below();
                if (isValidSubstrate(level.getBlockState(below))) {
                    level.setBlock(target, BioForge.MICROBIAL_MAT.get().defaultBlockState()
                            .setValue(MicrobialMatBlock.GROWTH, 0)
                            .setValue(MicrobialMatBlock.HOST_CROP, false), 3);
                    if (level.getBlockEntity(target) instanceof MicrobialMatBlockEntity mat) {
                        mat.setStrainData(strainData);
                        mat.setCorePos(corePos);
                    }
                }
            }
        }
    }

    private boolean isWithinInfluence(ServerLevel level) {
        if (corePos == null) return false;
        BlockState coreState = level.getBlockState(corePos);
        return coreState.is(BioForge.COLONY_CORE.get()) && worldPosition.closerThan(corePos, colonyRadius);
    }

    private void die(ServerLevel level, BlockPos pos) {
        if (corePos != null) {
            BlockEntity be = level.getBlockEntity(corePos);
            if (be instanceof ColonyCoreBlockEntity core) {
                core.decrementInfestedCount();
            }
        }
        if (hostState != null) {
            level.setBlock(pos, hostState, 3);
        } else {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
        }
    }

    private void generateResources(ServerLevel level) {
        if (corePos != null) {
            BlockEntity be = level.getBlockEntity(corePos);
            if (be instanceof ColonyCoreBlockEntity core) {
                core.addResources(1);
            }
        }
    }

    private void attemptUndergroundSpread(ServerLevel level, BlockPos pos) {
        RandomSource random = level.random;
        if (random.nextFloat() < 0.7f) {
            BlockPos down = pos.below();
            BlockState downState = level.getBlockState(down);
            if (isValidSubstrate(downState) && !downState.is(BioForge.INFESTED_BLOCK.get())) {
                if (tryCreateInfestedBlock(level, down, downState)) return;
            }
        }
        int dx = random.nextInt(3) - 1;
        int dz = random.nextInt(3) - 1;
        int dy = random.nextFloat() < 0.5f ? -1 : 0;
        BlockPos target = pos.offset(dx, dy, dz);
        if (target.equals(pos)) return;
        BlockState targetState = level.getBlockState(target);
        if (!isValidSubstrate(targetState) || targetState.is(BioForge.INFESTED_BLOCK.get())) return;
        tryCreateInfestedBlock(level, target, targetState);
    }

    private boolean tryCreateInfestedBlock(ServerLevel level, BlockPos pos, BlockState originalState) {
        if (corePos != null) {
            BlockEntity be = level.getBlockEntity(corePos);
            if (be instanceof ColonyCoreBlockEntity core) {
                if (!core.canCreateInfestedBlock() || !core.consumeResources(1)) return false;
                core.incrementInfestedCount();
            }
        }
        level.setBlock(pos, BioForge.INFESTED_BLOCK.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof InfestedBlockEntity newInfested) {
            newInfested.setStrainData(strainData);
            newInfested.setCorePos(corePos);
            newInfested.hostState = originalState;
        }
        return true;
    }

    private float getGrowthChance() {
        if (pathogen == null) return 0.0f;
        return switch (pathogen) {
            case FUNGI -> 0.3f;
            case BACTERIA -> 0.15f;
            case PARASITE -> 0.05f;
            case PRION -> 0.02f;
            default -> 0.0f;
        };
    }

    private boolean isValidSubstrate(BlockState state) {
        return state.is(BlockTags.create(ResourceLocation.tryBuild("bioforge", "substrate/organic")));
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (strainData != null) NbtObfuscator.writeString(tag, strainData);
        if (corePos != null) {
            tag.putInt("CX", corePos.getX());
            tag.putInt("CY", corePos.getY());
            tag.putInt("CZ", corePos.getZ());
        }
        if (hostState != null) tag.putString("Host", NbtUtils.writeBlockState(hostState).toString());
        tag.putInt("TickCounter", tickCounter);
        tag.putInt("ColonyRadius", colonyRadius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (NbtObfuscator.hasData(tag)) {
            String decrypted = NbtObfuscator.readString(tag);
            if (decrypted != null) setStrainData(decrypted);
        }
        if (tag.contains("CX")) {
            corePos = new BlockPos(tag.getInt("CX"), tag.getInt("CY"), tag.getInt("CZ"));
        }
        if (tag.contains("Host")) {
            try {
                hostState = NbtUtils.readBlockState(
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
                        net.minecraft.nbt.TagParser.parseTag(tag.getString("Host"))
                );
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                hostState = null;
            }
        }
        tickCounter = tag.getInt("TickCounter");
        colonyRadius = tag.getInt("ColonyRadius");
    }

    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { CompoundTag t = super.getUpdateTag(registries); saveAdditional(t, registries); return t; }
    @Override public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) { super.handleUpdateTag(tag, registries); loadAdditional(tag, registries); }
    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) { if (pkt.getTag() != null) handleUpdateTag(pkt.getTag(), registries); }
}
