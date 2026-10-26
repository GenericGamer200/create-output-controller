package com.wizard.outputcontroller;

import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class OutputControllerBlock extends AbstractEncasedShaftBlock implements IBE<OutputControllerBlockEntity> {

    public OutputControllerBlock(Properties properties) { super(properties); }

    @Override
public BlockState getStateForPlacement(BlockPlaceContext ctx) {
    Level level = ctx.getLevel();
    BlockPos pos = ctx.getClickedPos();
    Direction.Axis spinning = null;   // neighbour that is already rotating
    Direction.Axis connectable = null; // neighbour with a shaft facing us, not rotating

    for (Direction d : Direction.values()) {
        BlockPos np = pos.relative(d);
        BlockState ns = level.getBlockState(np);
        if (level.getBlockEntity(np) instanceof KineticBlockEntity kbe
                && ns.getBlock() instanceof IRotate rot
                && rot.hasShaftTowards(level, np, ns, d.getOpposite())) {
            if (kbe.getTheoreticalSpeed() != 0) { spinning = d.getAxis(); break; }
            if (connectable == null) connectable = d.getAxis();
        }
    }

    Direction.Axis axis = spinning != null ? spinning
            : connectable != null ? connectable
            : ctx.getClickedFace().getAxis();
    return defaultBlockState().setValue(AXIS, axis);
}

@Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    // wrench: let Create's wrench handle rotating/dismantling
    if (AllItems.WRENCH.isIn(player.getMainHandItem()) || AllItems.WRENCH.isIn(player.getOffhandItem()))
        return InteractionResult.PASS;
    // input/output ends: no GUI, so shafts and cogs can be placed there
    if (hit.getDirection().getAxis() == state.getValue(AXIS))
        return InteractionResult.PASS;

    if (level.isClientSide) com.wizard.outputcontroller.client.ClientHooks.openScreen(pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
}

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide) withBlockEntityDo(level, pos, OutputControllerBlockEntity::onRedstoneChanged);
    }

    @Override public Class<OutputControllerBlockEntity> getBlockEntityClass() { return OutputControllerBlockEntity.class; }
    @Override public BlockEntityType<? extends OutputControllerBlockEntity> getBlockEntityType() {
        return OutputControllerMod.OUTPUT_CONTROLLER_BE.get();
    }
}