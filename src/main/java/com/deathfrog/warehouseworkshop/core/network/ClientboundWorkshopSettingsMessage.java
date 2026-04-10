package com.deathfrog.warehouseworkshop.core.network;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.client.network.ClientWorkshopSettingsHandler;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopPlayerSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sends the authoritative persisted workshop settings for the current player.
 */
public record ClientboundWorkshopSettingsMessage(BlockPos buildingPos, int outputTargetId, boolean includePlayerInventory) implements CustomPacketPayload
{
    @SuppressWarnings("null")
    public static final Type<ClientboundWorkshopSettingsMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "workshop_settings"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWorkshopSettingsMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        ClientboundWorkshopSettingsMessage::buildingPos,
        ByteBufCodecs.INT,
        ClientboundWorkshopSettingsMessage::outputTargetId,
        ByteBufCodecs.BOOL,
        ClientboundWorkshopSettingsMessage::includePlayerInventory,
        ClientboundWorkshopSettingsMessage::new);

    @Override
    public Type<ClientboundWorkshopSettingsMessage> type()
    {
        return ID;
    }

    /**
     * Sends the given workshop settings to the given player.
     *
     * @param player the player to send the settings to
     * @param buildingPos the building position to associate with the settings
     * @param settings the settings to send
     */
    public static void sendToPlayer(final @Nonnull ServerPlayer player, final BlockPos buildingPos, final WorkshopPlayerSettings settings)
    {
        PacketDistributor.sendToPlayer(player, new ClientboundWorkshopSettingsMessage(
            buildingPos,
            settings.outputTarget().getId(),
            settings.includePlayerInventory()));
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> ClientWorkshopSettingsHandler.handle(buildingPos, OutputTarget.byId(outputTargetId), includePlayerInventory));
    }
}
