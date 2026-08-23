package net.jenkimods.bioforge.registry;

import net.jenkimods.bioforge.BioForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class BioForgeCapabilities {
    private BioForgeCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                BioForge.CENTRIFUGE_BE.get(), (blockEntity, side) ->
                        blockEntity.getItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                BioForge.INCUBATOR_BE.get(), (blockEntity, side) ->
                        blockEntity.getItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                BioForge.MICROSCOPE_BE.get(), (blockEntity, side) ->
                        blockEntity.getItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                BioForge.VACCINE_MAKER_BE.get(), (blockEntity, side) ->
                        blockEntity.getItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                BioForge.LABORATORY_PROCESSOR_BE.get(), (blockEntity, side) ->
                        blockEntity.getItemHandler());
    }
}
