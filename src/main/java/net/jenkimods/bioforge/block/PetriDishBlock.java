package net.jenkimods.bioforge.block;

import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.item.infection.PetriDishBlockEntity;
import net.jenkimods.bioforge.item.infection.SwabItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PetriDishBlock extends BaseEntityBlock {
    public static final MapCodec<PetriDishBlock> CODEC = simpleCodec(ignored -> new PetriDishBlock());

    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, 4);

    private static final VoxelShape SHAPE = Shapes.box(3.0/16.0, 0.0, 3.0/16.0,
            13.0/16.0, 3.0/16.0, 13.0/16.0);

    public PetriDishBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(0.5F)
                .noOcclusion()
                .isViewBlocking((a, b, c) -> false)
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetriDishBlockEntity(pos, state);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        int growth = 0;
        if (net.jenkimods.bioforge.util.StackData.has(stack) && net.jenkimods.bioforge.util.StackData.copy(stack).contains("Growth")) {
            growth = net.jenkimods.bioforge.util.StackData.copy(stack).getInt("Growth");
        }
        BlockPos pos = ctx.getClickedPos();
        return canSurvive(defaultBlockState(), ctx.getLevel(), pos)
                ? defaultBlockState().setValue(GROWTH, growth)
                : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter reader, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PetriDishBlockEntity dish && dish.attemptGrowth(random)) {
            level.setBlock(pos, state.setValue(GROWTH, dish.growthStage), 3);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PetriDishBlockEntity dish) {
            String data = NbtObfuscator.readString(stack);
            if (data != null) {
                dish.setStrainData(data);
                int growth = net.jenkimods.bioforge.util.StackData.copy(stack).getInt("Growth");
                dish.growthStage = growth;
                level.setBlock(pos, state.setValue(GROWTH, growth), 3);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(held.getItem() instanceof SwabItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PetriDishBlockEntity dish)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!dish.isInoculated()) {
            if (!held.isEmpty() && held.getItem() instanceof SwabItem && SwabItem.isContaminated(held)) {
                String data = NbtObfuscator.readString(held);
                if (data == null) return ItemInteractionResult.FAIL;

                dish.setStrainData(data);
                level.setBlock(pos, state.setValue(GROWTH, 0), 3);

                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable("item.bioforge.petri_dish.inoculated"));
                return ItemInteractionResult.CONSUME;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (dish.growthStage >= 3) {
            if (held.getItem() instanceof SwabItem && !SwabItem.isContaminated(held)) {
                if (dish.harvest(held, player)) {
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8f, 1.2f);
                    return ItemInteractionResult.CONSUME;
                }
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }


    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity be, ItemStack tool) {
        if (!level.isClientSide() && be instanceof PetriDishBlockEntity dish) {
            if (!player.isCreative()) {
                ItemStack drop = new ItemStack(BioForge.PETRI_DISH.get(), 1);
                dish.saveToStack(drop);
                popResource(level, pos, drop);
            }
        }
        level.removeBlockEntity(pos);
    }
}
