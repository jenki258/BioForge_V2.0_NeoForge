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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MicrobialMatBlockEntity extends BlockEntity {

    private String strainData = null;
    public PathogenType pathogen = null;
    public Set<InfectionType> infectionTypes = EnumSet.noneOf(InfectionType.class);
    public UUID colonyId = null;

    private static final int SPOROCARP_RADIUS = 10;
    private static final float MAT_SPREAD_COST = 1.0f;
    private static final int INFESTED_CONVERSION_COST = 2;

    @Nullable
    private BlockPos corePos = null;
    private int colonyRadius = 20;

    public MicrobialMatBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.MICROBIAL_MAT_BE.get(), pos, state);
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

    @Nullable
    public BlockPos getCorePos() { return corePos; }

    public void randomTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (strainData == null) return;

        if (!isWithinInfluence(level, pos)) {
            level.setBlock(pos, BioForge.NECROTIC_PATCH.get().defaultBlockState(), 3);
            return;
        }

        if (pathogen != PathogenType.FUNGI) {
            if (level.getBrightness(LightLayer.SKY, pos) >= 10) {
                level.setBlock(pos, BioForge.NECROTIC_PATCH.get().defaultBlockState(), 3);
                return;
            }
        }

        int growth = state.getValue(MicrobialMatBlock.GROWTH);

        if (growth == 4 && pathogen == PathogenType.FUNGI) {
            if (!hasNearbySporocarp(level, pos)) {
                level.setBlock(pos, BioForge.SPOROCARP.get().defaultBlockState(), 3);
                if (level.getBlockEntity(pos) instanceof SporocarpBlockEntity spore) {
                    spore.setStrainData(strainData);
                    spore.setCorePos(corePos);
                }
            }
            return;
        }

        if (growth < 4 && random.nextFloat() < getGrowthChance()) {
            if (consumeResources(1)) {
                level.setBlock(pos, state.setValue(MicrobialMatBlock.GROWTH, growth + 1), 3);
                setChanged();
            }
        }

        if (growth >= 2 && random.nextFloat() < 0.15f) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (isValidSubstrate(belowState) && !belowState.is(BioForge.INFESTED_BLOCK.get())) {
                if (corePos != null) {
                    BlockEntity be = level.getBlockEntity(corePos);
                    if (be instanceof ColonyCoreBlockEntity core) {
                        if (core.canCreateInfestedBlock() && core.consumeResources(INFESTED_CONVERSION_COST)) {
                            core.incrementInfestedCount();
                            level.setBlock(below, BioForge.INFESTED_BLOCK.get().defaultBlockState(), 3);
                            if (level.getBlockEntity(below) instanceof InfestedBlockEntity infested) {
                                infested.setStrainData(strainData);
                                infested.setCorePos(corePos);
                                infested.hostState = belowState;
                            }
                        }
                    }
                }
            }
        }

        if (pathogen != PathogenType.VIRUS && growth >= 1 && random.nextFloat() < getSpreadChance()) {
            if (consumeResources(MAT_SPREAD_COST)) {
                attemptSpread(level, pos, random);
            }
        }
    }

    private boolean consumeResources(float amount) {
        if (corePos == null) return false;
        BlockEntity be = level.getBlockEntity(corePos);
        if (be instanceof ColonyCoreBlockEntity core) {
            return core.consumeResources((int)amount);
        }
        return false;
    }

    private boolean isWithinInfluence(ServerLevel level, BlockPos pos) {
        if (corePos == null) return false;
        BlockState coreState = level.getBlockState(corePos);
        if (!coreState.is(BioForge.COLONY_CORE.get())) return false;
        return pos.closerThan(corePos, colonyRadius);
    }

    private boolean hasNearbySporocarp(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int x = -SPOROCARP_RADIUS; x <= SPOROCARP_RADIUS; x++) {
            for (int y = -SPOROCARP_RADIUS; y <= SPOROCARP_RADIUS; y++) {
                for (int z = -SPOROCARP_RADIUS; z <= SPOROCARP_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    mPos.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (level.getBlockState(mPos).is(BioForge.SPOROCARP.get())) return true;
                }
            }
        }
        return false;
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

    private float getSpreadChance() {
        if (pathogen == null) return 0.0f;
        return switch (pathogen) {
            case FUNGI -> 0.25f;
            case BACTERIA -> 0.10f;
            case PARASITE -> 0.04f;
            case PRION -> 0.01f;
            default -> 0.0f;
        };
    }

    private void attemptSpread(ServerLevel level, BlockPos pos, RandomSource random) {
        int dx = random.nextInt(3) - 1;
        int dz = random.nextInt(3) - 1;
        int dy = random.nextFloat() < 0.3f ? 1 : 0;
        BlockPos targetPos = pos.offset(dx, dy, dz);
        if (targetPos.equals(pos)) return;
        if (!level.getBlockState(targetPos).isAir()) return;
        BlockPos below = targetPos.below();
        if (!isValidSubstrate(level.getBlockState(below))) return;

        level.setBlock(targetPos, BioForge.MICROBIAL_MAT.get().defaultBlockState()
                .setValue(MicrobialMatBlock.GROWTH, 0)
                .setValue(MicrobialMatBlock.HOST_CROP, false), 3);
        if (level.getBlockEntity(targetPos) instanceof MicrobialMatBlockEntity newMat) {
            newMat.setStrainData(strainData);
            newMat.setCorePos(corePos);
        }
    }

    private boolean isValidSubstrate(BlockState state) {
        return state.is(BlockTags.create(ResourceLocation.tryBuild("bioforge", "substrate/organic")));
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (strainData != null) NbtObfuscator.writeString(tag, strainData);
        if (corePos != null) {
            tag.putInt("CoreX", corePos.getX());
            tag.putInt("CoreY", corePos.getY());
            tag.putInt("CoreZ", corePos.getZ());
        }
        tag.putInt("ColonyRadius", colonyRadius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (NbtObfuscator.hasData(tag)) {
            String decrypted = NbtObfuscator.readString(tag);
            if (decrypted != null) setStrainData(decrypted);
        }
        if (tag.contains("CoreX")) {
            corePos = new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ"));
        }
        colonyRadius = tag.getInt("ColonyRadius");
    }

    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { CompoundTag t = super.getUpdateTag(registries); saveAdditional(t, registries); return t; }
    @Override public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) { super.handleUpdateTag(tag, registries); loadAdditional(tag, registries); }
    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) { if (pkt.getTag() != null) handleUpdateTag(pkt.getTag(), registries); }
}
