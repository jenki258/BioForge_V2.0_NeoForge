package net.jenkimods.bioforge.block;

import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessorBlockEntity;
import net.jenkimods.bioforge.world.laboratory.LaboratoryStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class LaboratoryProcessorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape BARREL_PRESS_SHAPE =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 13.5D, 16.0D);
    private final LaboratoryStation station;
    private final MapCodec<LaboratoryProcessorBlock> codec;

    public LaboratoryProcessorBlock(LaboratoryStation station) {
        super(properties(station));
        this.station = station;
        this.codec = simpleCodec(ignored -> new LaboratoryProcessorBlock(station));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    private static BlockBehaviour.Properties properties(LaboratoryStation station) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .strength(station == LaboratoryStation.BARREL_PRESS
                        ? 2.5F : 4.0F)
                .sound(station == LaboratoryStation.BARREL_PRESS ? SoundType.WOOD : SoundType.METAL)
                .requiresCorrectToolForDrops();
        return station == LaboratoryStation.BARREL_PRESS ? properties.noOcclusion() : properties;
    }

    public LaboratoryStation station() {
        return station;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LaboratoryProcessorBlockEntity processor) {
            serverPlayer.openMenu(processor, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaboratoryProcessorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, BioForge.LABORATORY_PROCESSOR_BE.get(),
                LaboratoryProcessorBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return station == LaboratoryStation.BARREL_PRESS
                ? BARREL_PRESS_SHAPE : super.getShape(state, level, pos, context);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock()
                && level.getBlockEntity(pos) instanceof LaboratoryProcessorBlockEntity processor) {
            processor.dropContents();
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
