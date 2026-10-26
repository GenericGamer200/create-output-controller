package com.wizard.outputcontroller;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wizard.outputcontroller.OutputControllerBlockEntity.Rule;

public record SaveConfigPayload(BlockPos pos, double defaultOutput, List<Rule> rules) implements CustomPacketPayload {

    public static final Type<SaveConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OutputControllerMod.MODID, "save_config"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Rule> RULE_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Rule::side,
            ByteBufCodecs.DOUBLE, Rule::offValue,
            ByteBufCodecs.DOUBLE, Rule::onValue,
            Rule::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveConfigPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SaveConfigPayload::pos,
            ByteBufCodecs.DOUBLE, SaveConfigPayload::defaultOutput,
            RULE_CODEC.apply(ByteBufCodecs.list(OutputControllerBlockEntity.MAX_RULES)), SaveConfigPayload::rules,
            SaveConfigPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveConfigPayload p, IPayloadContext ctx) {
        var player = ctx.player();
        var level = player.level();
        if (!level.isLoaded(p.pos())) return;
        if (player.distanceToSqr(Vec3.atCenterOf(p.pos())) > 64) return; // anti-cheat: must be near
        if (level.getBlockEntity(p.pos()) instanceof OutputControllerBlockEntity be) {
            be.setConfig(p.defaultOutput(), p.rules());
        }
    }
}