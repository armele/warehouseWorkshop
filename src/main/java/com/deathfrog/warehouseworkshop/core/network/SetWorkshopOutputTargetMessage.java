package com.deathfrog.warehouseworkshop.core.network;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopPlayerSettings;
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
 * Persists the selected workshop crafting output destination for the acting player.
 */
public record SetWorkshopOutputTargetMessage(BlockPos buildingPos, int outputTargetId) implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<SetWorkshopOutputTargetMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "set_workshop_output_target"));
    
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SetWorkshopOutputTargetMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetWorkshopOutputTargetMessage::buildingPos,
        ByteBufCodecs.INT,
        SetWorkshopOutputTargetMessage::outputTargetId,
        SetWorkshopOutputTargetMessage::new);

    @Override
    public Type<SetWorkshopOutputTargetMessage> type()
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
            final WorkshopPlayerSettings currentSettings = WorkshopPlayerSettings.get(player, buildingPos, module);
            WorkshopPlayerSettings.save(player, buildingPos, new WorkshopPlayerSettings(OutputTarget.byId(outputTargetId), currentSettings.includePlayerInventory()));
        }
    }
}
