package com.teammetallurgy.aquaculture.block;

import com.teammetallurgy.aquaculture.block.tileentity.TackleBoxTileEntity;
import com.teammetallurgy.aquaculture.misc.StackHelper;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.IFluidState;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.stats.Stats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TackleBoxBlock extends ContainerBlock {
    public static final DirectionProperty FACING = HorizontalBlock.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.makeCuboidShape(1.0D, 0.0D, 0.0D, 15.0D, 14.0D, 15.0D); //TODO

    public TackleBoxBlock() {
        super(Block.Properties.create(Material.WOOD).hardnessAndResistance(2.0F).sound(SoundType.METAL));
        this.setDefaultState(this.getStateContainer().getBaseState().with(FACING, Direction.NORTH).with(WATERLOGGED, false));
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@Nonnull IBlockReader blockReader) {
        return new TackleBoxTileEntity();
    }

    @Override
    public boolean hasCustomBreakingProgress(BlockState state) {
        return true;
    }

    @Override
    @Nonnull
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nonnull
    public VoxelShape getShape(BlockState state, IBlockReader blockReader, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    @Override
    public boolean onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (world.isRemote) {
            return true;
        } else {
            INamedContainerProvider container = this.getContainer(state, world, pos);
            if (container != null && player instanceof ServerPlayerEntity) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                if (player.isSneaking()) {
                    TileEntity tileEntity = world.getTileEntity(pos);
                    if (tileEntity != null) {
                        StackHelper.giveItem(serverPlayer, hand, StackHelper.storeTEInStack(new ItemStack(this), tileEntity));
                        world.removeBlock(pos, false);
                        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    }
                } else {
                    NetworkHooks.openGui(serverPlayer, container, pos);
                }
            }
            return true;
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        Direction placerFacing = context.getPlacementHorizontalFacing().getOpposite();
        IFluidState fluidState = context.getWorld().getFluidState(context.getPos());
        boolean isSneaking = context.isPlacerSneaking();
        Direction direction = context.getFace();
        if (direction.getAxis().isHorizontal() && isSneaking) {
            Direction attachDirection = context.getWorld().getBlockState(context.getPos().offset(direction.getOpposite())).get(FACING);
            if (attachDirection.getAxis() != direction.getAxis()) {
                placerFacing = attachDirection;
            }
        }
        return this.getDefaultState().with(FACING, placerFacing).with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, @Nonnull ItemStack stack) {
        if (stack.hasDisplayName()) {
            TileEntity tileentity = world.getTileEntity(pos);
            if (tileentity instanceof TackleBoxTileEntity) {
                ((TackleBoxTileEntity) tileentity).setCustomName(stack.getDisplayName());
            }
        }
    }

    @Override
    public void onReplaced(BlockState state, @Nonnull World world, @Nonnull BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            this.dropInventory(world, pos);
            world.updateComparatorOutputLevel(pos, this);
            super.onReplaced(state, world, pos, newState, isMoving);
        }
    }

    @Override
    public boolean hasComparatorInputOverride(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(BlockState state, World world, BlockPos pos) {
        return ItemHandlerHelper.calcRedstoneFromInventory(world.getTileEntity(pos).getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).orElse(null));
    }

    @Override
    @Nonnull
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    @Nonnull
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.toRotation(state.get(FACING)));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public boolean allowsMovement(@Nonnull BlockState state, @Nonnull IBlockReader blockReader, @Nonnull BlockPos pos, PathType type) {
        return false;
    }

    @Override
    public void harvestBlock(@Nonnull World world, PlayerEntity player, @Nonnull BlockPos pos, BlockState state, @Nullable TileEntity tileEntity, @Nonnull ItemStack stack) {
        player.addStat(Stats.BLOCK_MINED.get(this));
        player.addExhaustion(0.005F);
        if (tileEntity != null) {
            spawnAsEntity(world, pos, StackHelper.storeTEInStack(new ItemStack(this), tileEntity));
        }
    }

    @Override
    public void onBlockExploded(BlockState state, World world, BlockPos pos, Explosion explosion) {
        if (!world.isRemote) {
            this.dropInventory(world, pos);
        }
        super.onBlockExploded(state, world, pos, explosion);
    }

    private void dropInventory(World world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity != null) {
            tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).ifPresent(handler -> StackHelper.dropInventory(world, pos, handler));
        }
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, IBlockReader world, BlockPos pos, Explosion explosion) {
        return false;
    }
}