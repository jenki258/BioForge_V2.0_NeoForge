package net.jenkimods.bioforge.block;

import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class ViralScannerBlock extends HorizontalDirectionalBlock {

    public enum Variant {
        FULL,
        CEILING,
        OPEN_LEFT,
        OPEN_RIGHT
    }

    public static final EnumProperty<DoubleBlockHalf> HALF =
            BlockStateProperties.DOUBLE_BLOCK_HALF;

    public static final BooleanProperty POWERED =
            BlockStateProperties.POWERED;

    private static final VoxelShape TOP_X =
            Block.box(0, 14, 0, 16, 16, 16);

    private static final VoxelShape TOP_Z =
            Block.box(0, 14, 0, 16, 16, 16);

    private static final VoxelShape LEFT_X =
            Block.box(0, 0, 0, 2, 16, 16);

    private static final VoxelShape RIGHT_X =
            Block.box(14, 0, 0, 16, 16, 16);

    private static final VoxelShape LEFT_Z =
            Block.box(0, 0, 0, 16, 16, 2);

    private static final VoxelShape RIGHT_Z =
            Block.box(0, 0, 14, 16, 16, 16);

    private final Variant variant;
    private final MapCodec<ViralScannerBlock> codec;

    public ViralScannerBlock(Variant variant) {
        super(
                BlockBehaviour.Properties.of()
                        .strength(3.5F, 8.0F)
                        .requiresCorrectToolForDrops()
                        .noOcclusion()
        );

        this.variant = variant;
        this.codec = simpleCodec(
                ignored -> new ViralScannerBlock(variant)
        );

        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(HALF, DoubleBlockHalf.LOWER)
                        .setValue(POWERED, false)
        );
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        if (
                variant != Variant.CEILING
                        && (
                        pos.getY() >= level.getMaxBuildHeight() - 1
                                || !level.getBlockState(pos.above())
                                .canBeReplaced(context)
                )
        ) {
            return null;
        }

        return defaultBlockState()
                .setValue(
                        FACING,
                        context.getHorizontalDirection().getOpposite()
                );
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            net.minecraft.world.item.ItemStack stack
    ) {
        if (variant != Variant.CEILING) {
            level.setBlock(
                    pos.above(),
                    state.setValue(
                            HALF,
                            DoubleBlockHalf.UPPER
                    ),
                    3
            );
        }

        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (variant == Variant.CEILING) {
            return direction == Direction.UP
                    && !state.canSurvive(level, pos)
                    ? net.minecraft.world.level.block.Blocks.AIR
                    .defaultBlockState()
                    : state;
        }

        DoubleBlockHalf half = state.getValue(HALF);

        Direction pairedDirection =
                half == DoubleBlockHalf.LOWER
                        ? Direction.UP
                        : Direction.DOWN;

        if (
                direction == pairedDirection
                        && (
                        !neighbor.is(this)
                                || neighbor.getValue(HALF) == half
                )
        ) {
            return net.minecraft.world.level.block.Blocks.AIR
                    .defaultBlockState();
        }

        return state;
    }

    @Override
    public boolean canSurvive(
            BlockState state,
            net.minecraft.world.level.LevelReader level,
            BlockPos pos
    ) {
        return variant != Variant.CEILING
                || level.getBlockState(pos.above())
                .isFaceSturdy(
                        level,
                        pos.above(),
                        Direction.DOWN
                );
    }

    @Override
    public void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean moving
    ) {
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        BlockPos controller = controllerPos(state, pos);

        BlockState controllerState =
                level.getBlockState(controller);

        if (!controllerState.is(this)) {
            return;
        }

        boolean detected =
                level.getEntitiesOfClass(
                                LivingEntity.class,
                                scanBounds(controller),
                                entity ->
                                        entity.isAlive()
                                                && isInfected(entity)
                        )
                        .stream()
                        .findAny()
                        .isPresent();

        setPowered(level, controller, detected);

        level.scheduleTick(controller, this, 10);
    }

    private boolean isInfected(LivingEntity entity) {
        var data = InfectionCapability.get(entity);

        return data != null
                && data.isInfected()
                && !MutationManager.hasMutationTag(
                data,
                "scanner_evasion"
        );
    }

    private AABB scanBounds(BlockPos pos) {

        if (variant == Variant.CEILING) {
            return new AABB(
                    pos.getX() - 0.35D,
                    pos.getY() - 3.0D,
                    pos.getZ() - 0.35D,

                    pos.getX() + 1.35D,
                    pos.getY() + 0.25D,
                    pos.getZ() + 1.35D
            );
        }

        return new AABB(
                pos.getX() - 0.2D,
                pos.getY(),
                pos.getZ() - 0.2D,

                pos.getX() + 1.2D,
                pos.getY() + 2.0D,
                pos.getZ() + 1.2D
        );
    }

    private void setPowered(
            ServerLevel level,
            BlockPos controller,
            boolean powered
    ) {
        BlockState lower =
                level.getBlockState(controller);

        if (
                !lower.is(this)
                        || lower.getValue(POWERED) == powered
        ) {
            return;
        }

        level.setBlock(
                controller,
                lower.setValue(POWERED, powered),
                3
        );

        level.updateNeighborsAt(controller, this);

        if (variant != Variant.CEILING) {

            BlockPos upperPos = controller.above();

            BlockState upper =
                    level.getBlockState(upperPos);

            if (upper.is(this)) {
                level.setBlock(
                        upperPos,
                        upper.setValue(
                                POWERED,
                                powered
                        ),
                        3
                );
            }
        }
    }

    private BlockPos controllerPos(
            BlockState state,
            BlockPos pos
    ) {
        return variant != Variant.CEILING
                && state.getValue(HALF)
                == DoubleBlockHalf.UPPER
                ? pos.below()
                : pos;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction side
    ) {
        return state.getValue(POWERED)
                ? 15
                : 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private VoxelShape scannerShape(BlockState state) {
        Direction facing = state.getValue(FACING);

        // Left/right are calculated RELATIVE to the direction
        // the scanner is facing, not just its X/Z axis.
        Direction leftDirection = facing.getCounterClockWise();
        Direction rightDirection = facing.getClockWise();

        VoxelShape left = wallShape(leftDirection);
        VoxelShape right = wallShape(rightDirection);

        VoxelShape top = TOP_X; // TOP_X and TOP_Z are identical anyway

        if (variant == Variant.CEILING) {
            return top;
        }

        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return switch (variant) {
                case OPEN_LEFT -> right;
                case OPEN_RIGHT -> left;
                default -> Shapes.or(left, right);
            };
        }

        return switch (variant) {
            case OPEN_LEFT -> Shapes.or(top, right);
            case OPEN_RIGHT -> Shapes.or(top, left);
            default -> Shapes.or(top, left, right);
        };
    }

    private VoxelShape wallShape(Direction direction) {
        return switch (direction) {
            case WEST -> LEFT_X;
            case EAST -> RIGHT_X;
            case NORTH -> LEFT_Z;
            case SOUTH -> RIGHT_Z;
            default -> Shapes.empty();
        };
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return scannerShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return scannerShape(state);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(
                FACING,
                HALF,
                POWERED
        );
    }
}