package net.jenkimods.bioforge.block;

import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.infection.InfestedBlockEntity;
import net.jenkimods.bioforge.item.infection.SwabItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class InfestedBlock extends BaseEntityBlock {
    public static final MapCodec<InfestedBlock> CODEC = simpleCodec(ignored -> new InfestedBlock());

    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, 4);

    public InfestedBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F)
                .noOcclusion()
                .randomTicks());
        registerDefaultState(stateDefinition.any().setValue(GROWTH, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GROWTH);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfestedBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof InfestedBlockEntity entity) entity.randomTick(level, pos, state, random);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(held.getItem() instanceof SwabItem) || SwabItem.isContaminated(held)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InfestedBlockEntity mat)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (held.getItem() instanceof SwabItem && !SwabItem.isContaminated(held)) {
            if (mat.getStrainData() != null) {
                NbtObfuscator.writeString(held, mat.getStrainData());
                player.setItemInHand(hand, held);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable(
                        "item.bioforge.swab.collected_mat").withStyle(ChatFormatting.GREEN));
                return ItemInteractionResult.CONSUME;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isClientSide() || !(entity instanceof LivingEntity living)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof InfestedBlockEntity mat)) return;

        if (mat.pathogen == null || mat.pathogen != PathogenType.FUNGI) return;

        InfectionData data = InfectionCapability.get(living);
        if (data == null) return;

        String strainRaw = mat.getStrainData();
        if (strainRaw == null || strainRaw.equals("CLEAN")) return;

        StrainData strain = StrainData.parse(strainRaw);
        float newStrength = mat.infectionStrength;

        if (!data.isInfected()) {
            strain.applyToEntity(data, living);
        } else {
            float oldStrength = data.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH);
            float r1 = 0.8f + level.random.nextFloat() * 0.4f;
            float r2 = 0.8f + level.random.nextFloat() * 0.4f;
            float ns = newStrength * r1;
            float os = oldStrength * r2;

            if (ns > os * 1.5f) {
                strain.applyToEntity(data, living);
            } else if (ns > os * 1.0f) {
                float avgStr = (oldStrength + newStrength) / 2f;
                data.setSymptom(BioForgeSymptoms.INFECTION_STRENGTH, avgStr);
            } else if (ns > os * 0.7f) {
                data.setSymptom(BioForgeSymptoms.INFECTION_STRENGTH,
                        Math.min(1.0f, oldStrength + 0.05f));
            }
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity be, ItemStack tool) {
        if (!level.isClientSide() && be instanceof InfestedBlockEntity mat) {
            if (!player.isCreative()) {
            }
        }
        level.removeBlockEntity(pos);
        super.playerDestroy(level, player, pos, state, be, tool);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InfestedBlockEntity mat && mat.hostState != null) {
                level.setBlock(pos, mat.hostState, 3);
                return;
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
