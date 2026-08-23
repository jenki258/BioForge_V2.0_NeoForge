package net.jenkimods.bioforge.item.infection;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.block.PetriDishBlock;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PetriDishBlockEntity extends BlockEntity {

    private String strainData = null;
    public boolean preventDrop = false;

    public int growthStage = 0;
    public PathogenType pathogen = null;
    public Set<InfectionType> infectionTypes = EnumSet.noneOf(InfectionType.class);

    public PetriDishBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.PETRI_DISH_BE.get(), pos, state);
    }

    public boolean isInoculated() {
        return strainData != null;
    }

    public void setStrainData(String encrypted) {
        this.strainData = encrypted;
        if (encrypted != null && !encrypted.equals("CLEAN")) {
            StrainData strain = StrainData.parse(encrypted);
            this.pathogen = strain.getPathogen();
            this.infectionTypes.clear();
            this.infectionTypes.addAll(strain.getInfectionTypes());
        } else {
            pathogen = null;
            infectionTypes.clear();
        }
        growthStage = 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String getStrainData() {
        return strainData;
    }

    public boolean attemptGrowth(RandomSource random) {
        if (!isInoculated() || pathogen == null || growthStage >= 4) return false;

        double chance = switch (pathogen) {
            case BACTERIA -> 0.3;
            case VIRUS    -> 0.2;
            case FUNGI    -> 0.25;
            case PARASITE -> 0.15;
            case PRION    -> 0.08;
            default       -> 0.2;
        };
        if (random.nextFloat() < chance) {
            growthStage++;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.setBlock(worldPosition, getBlockState().setValue(PetriDishBlock.GROWTH, growthStage), 3);
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return true;
        }
        return false;
    }

    public boolean consumeGrowth(int amount) {
        if (amount <= 0 || growthStage <= 0) return false;
        growthStage = Math.max(0, growthStage - amount);
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(PetriDishBlock.GROWTH)) {
                level.setBlock(worldPosition, state.setValue(PetriDishBlock.GROWTH, growthStage), 3);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public boolean harvest(ItemStack swab, Player player) {
        if (!isInoculated() || strainData == null) return false;
        if (growthStage < 3) return false;
        if (SwabItem.isContaminated(swab)) return false;

        NbtObfuscator.writeString(swab, strainData);

        if (growthStage == 3) {
            strainData = null;
            pathogen = null;
            infectionTypes.clear();
            growthStage = 0;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.setBlock(worldPosition, getBlockState().setValue(PetriDishBlock.GROWTH, 0), 3);
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        if (player != null) {
            player.sendSystemMessage(Component.translatable("item.bioforge.petri_dish.harvested"));
        }
        return true;
    }

    public void saveToStack(ItemStack stack) {
        if (strainData != null) {
            NbtObfuscator.writeString(stack, strainData);
            net.jenkimods.bioforge.util.StackData.update(stack,
                    tag -> tag.putInt("Growth", growthStage));
        }
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (strainData != null) {
            NbtObfuscator.writeString(tag, strainData);
        }
        tag.putInt("Growth", growthStage);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (NbtObfuscator.hasData(tag)) {
            String decrypted = NbtObfuscator.readString(tag);
            if (decrypted != null) {
                this.strainData = decrypted;
                if (!decrypted.equals("CLEAN")) {
                    StrainData strain = StrainData.parse(decrypted);
                    this.pathogen = strain.getPathogen();
                    this.infectionTypes.clear();
                    this.infectionTypes.addAll(strain.getInfectionTypes());
                } else {
                    pathogen = null;
                    infectionTypes.clear();
                }
            }
        }
        growthStage = tag.getInt("Growth");
    }
}
