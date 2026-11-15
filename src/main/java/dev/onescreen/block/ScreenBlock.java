package dev.onescreen.block;

import com.mojang.serialization.MapCodec;
import dev.onescreen.editor.ScreenEditorNetworking;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class ScreenBlock extends BaseEntityBlock {
	public static final MapCodec<ScreenBlock> CODEC = simpleCodec(ScreenBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
		Direction.NORTH, Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D),
		Direction.SOUTH, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D),
		Direction.EAST, Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D),
		Direction.WEST, Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
		Direction.UP, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D),
		Direction.DOWN, Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D)
	);

	public ScreenBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HIDDEN, false));
	}

	@Override
	public MapCodec<ScreenBlock> codec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new ScreenBlockEntity(pos, state);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
	}

	@Override
	protected RenderShape getRenderShape(final BlockState state) {
		return state.getValue(HIDDEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useItemOn(
		final ItemStack itemStack,
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final InteractionHand hand,
		final BlockHitResult hitResult
	) {
		if (!level.isClientSide()) {
			ScreenEditorNetworking.tryOpen(player, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final BlockHitResult hitResult
	) {
		if (!level.isClientSide()) {
			ScreenEditorNetworking.tryOpen(player, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState rotate(final BlockState state, final Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(final BlockState state, final Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HIDDEN);
	}
}