package net.jenkimods.bioforge.world.decoration;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlackSteelTilesBlockEntity extends BlockEntity {
    public static final int VARIANT_COUNT = 12;
    private static final String FACE_VARIANTS_TAG = "FaceVariants";
    private static final String ITEM_VARIANTS_TAG = "BioForgeFaceVariants";

    private final byte[] faceVariants = new byte[Direction.values().length];

    public BlackSteelTilesBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.BLACK_STEEL_TILES_BE.get(), pos, state);
    }

    public int getVariant(Direction face) {
        return Byte.toUnsignedInt(faceVariants[face.ordinal()]);
    }

    public void cycleVariant(Direction face) {
        int index = face.ordinal();
        faceVariants[index] = (byte) ((getVariant(face) + 1) % VARIANT_COUNT);
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public void copyVariantsTo(ItemStack stack) {
        net.jenkimods.bioforge.util.StackData.update(stack,
                tag -> tag.putByteArray(ITEM_VARIANTS_TAG, faceVariants));
    }

    public void applyVariantsFrom(ItemStack stack) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        if (!tag.contains(ITEM_VARIANTS_TAG, Tag.TAG_BYTE_ARRAY)) return;
        byte[] saved = tag.getByteArray(ITEM_VARIANTS_TAG);
        for (int i = 0; i < faceVariants.length; i++) {
            int value = i < saved.length ? Byte.toUnsignedInt(saved[i]) : 0;
            faceVariants[i] = (byte) Math.floorMod(value, VARIANT_COUNT);
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray(FACE_VARIANTS_TAG, faceVariants);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte[] saved = tag.getByteArray(FACE_VARIANTS_TAG);
        for (int i = 0; i < faceVariants.length; i++) {
            int value = i < saved.length ? Byte.toUnsignedInt(saved[i]) : 0;
            faceVariants[i] = (byte) Math.floorMod(value, VARIANT_COUNT);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}
