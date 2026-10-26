package com.wizard.outputcontroller;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;

@Mod(OutputControllerMod.MODID)
public class OutputControllerMod {
    public static final String MODID = "outputcontroller";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<OutputControllerBlock> OUTPUT_CONTROLLER = BLOCKS.registerBlock(
            "output_controller", OutputControllerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> OUTPUT_CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem("output_controller", OUTPUT_CONTROLLER);

    public static final Supplier<BlockEntityType<OutputControllerBlockEntity>> OUTPUT_CONTROLLER_BE =
            BLOCK_ENTITIES.register("output_controller", () ->
                    BlockEntityType.Builder.of(OutputControllerBlockEntity::new, OUTPUT_CONTROLLER.get()).build(null));

    public OutputControllerMod(IEventBus modBus) {
    BLOCKS.register(modBus);
    ITEMS.register(modBus);
    BLOCK_ENTITIES.register(modBus);
    modBus.addListener(this::registerPayloads);
    modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
        if (e.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath("create", "base")))
            e.accept(OUTPUT_CONTROLLER_ITEM);
    });
}

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SaveConfigPayload.TYPE, SaveConfigPayload.CODEC, SaveConfigPayload::handle);
    }
}