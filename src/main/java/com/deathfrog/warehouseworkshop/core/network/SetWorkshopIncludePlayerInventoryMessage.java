package com.deathfrog.warehouseworkshop.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Persists whether workshop crafting may use the requesting player's inventory.
 */
public record SetWorkshopIncludePlayerInventoryMessage(BlockPos buildingPos, boolean includePlayerInventory) implements IServerboundPayload
{
    public static final Type<SetWorkshopIncludePlayerInventoryMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "set_workshop_include_player_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWorkshopIncludePlayerInventoryMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetWorkshopIncludePlayerInventoryMessage::buildingPos,
        ByteBufCodecs.BOOL,
        SetWorkshopIncludePlayerInventoryMessage::includePlayerInventory,
        SetWorkshopIncludePlayerInventoryMessage::new);

    @Override
    public Type<SetWorkshopIncludePlayerInventoryMessage> type()
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
        if (player == null)
        {
            return;
        }

        final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), buildingPos);
        if (building == null)
        {
            return;
        }

        final WorkshopModule module = building.getModule(WorkshopModule.class, candidate -> true);
        if (module != null)
        {
            module.setIncludePlayerInventory(includePlayerInventory);
        }
    }
}
