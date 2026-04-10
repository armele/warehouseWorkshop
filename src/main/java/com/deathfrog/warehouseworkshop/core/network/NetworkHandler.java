package com.deathfrog.warehouseworkshop.core.network;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers addon payloads.
 */
public final class NetworkHandler
{
    private NetworkHandler()
    {
    }

    @SuppressWarnings("null")
    public static void register(final RegisterPayloadHandlersEvent event)
    {
        final String modVersion = ModList.get().getModContainerById(WarehouseWorkshopMod.MODID).get().getModInfo().getVersion().toString();
        final PayloadRegistrar registrar = event.registrar(WarehouseWorkshopMod.MODID).versioned(modVersion);
        registrar.playToClient(ClientboundWorkshopCraftedMessage.ID, ClientboundWorkshopCraftedMessage.STREAM_CODEC, ClientboundWorkshopCraftedMessage::onExecute);
        registrar.playToClient(ClientboundWorkshopSettingsMessage.ID, ClientboundWorkshopSettingsMessage.STREAM_CODEC, ClientboundWorkshopSettingsMessage::onExecute);
        registrar.playToServer(WorkshopCraftMessage.ID, WorkshopCraftMessage.STREAM_CODEC, WorkshopCraftMessage::onExecute);
        registrar.playToServer(RequestWorkshopSettingsMessage.ID, RequestWorkshopSettingsMessage.STREAM_CODEC, RequestWorkshopSettingsMessage::onExecute);
        registrar.playToServer(SetWorkshopOutputTargetMessage.ID, SetWorkshopOutputTargetMessage.STREAM_CODEC, SetWorkshopOutputTargetMessage::onExecute);
        registrar.playToServer(SetWorkshopIncludePlayerInventoryMessage.ID, SetWorkshopIncludePlayerInventoryMessage.STREAM_CODEC, SetWorkshopIncludePlayerInventoryMessage::onExecute);
    }
}
