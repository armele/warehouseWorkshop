package com.deathfrog.warehouseworkshop.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopPlayerSettings;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Requests the acting player's persisted workshop settings for the viewed building.
 */
public record RequestWorkshopSettingsMessage(BlockPos buildingPos) implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<RequestWorkshopSettingsMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "request_workshop_settings"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWorkshopSettingsMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        RequestWorkshopSettingsMessage::buildingPos,
        RequestWorkshopSettingsMessage::new);

    @Override
    public Type<RequestWorkshopSettingsMessage> type()
    {
        return ID;
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        final Player player = context.player();
        context.enqueueWork(() -> execute(player));
    }

    private void execute(final Player player)
    {
        if (!(player instanceof final ServerPlayer serverPlayer))
        {
            return;
        }

        final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), buildingPos);
        if (building == null)
        {
            return;
        }

        final WorkshopModule module = building.getModule(WorkshopModule.class, candidate -> true);
        if (module == null)
        {
            return;
        }

        final WorkshopPlayerSettings settings = WorkshopPlayerSettings.get(player, buildingPos);
        ClientboundWorkshopSettingsMessage.sendToPlayer(serverPlayer, buildingPos, settings);
    }
}
