package com.wizard.outputcontroller.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

public final class ClientHooks {
    public static void openScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new OutputControllerScreen(pos));
    }

    public static Direction getLookedFace(BlockPos pos) {
    var hit = Minecraft.getInstance().hitResult;
    if (hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos)) return bhr.getDirection();
    return null;
}
}

