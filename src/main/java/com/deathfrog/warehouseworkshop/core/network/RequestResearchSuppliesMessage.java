package com.deathfrog.warehouseworkshop.core.network;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.ResearchSuppliesModule;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import com.deathfrog.warehouseworkshop.core.research.ResearchSuppliesConstants;

/** Requests an authoritative research-supply snapshot for one university. */
public record RequestResearchSuppliesMessage(BlockPos universityPos) implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<RequestResearchSuppliesMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, ResearchSuppliesConstants.REQUEST_SNAPSHOT_PAYLOAD));
    
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestResearchSuppliesMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestResearchSuppliesMessage::universityPos, RequestResearchSuppliesMessage::new);

    /** Returns this payload's identifier. */
    @Override
    public Type<RequestResearchSuppliesMessage> type()
    {
        return ID;
    }

    /** Executes the snapshot request on the server thread. */
    @SuppressWarnings("null")
    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), universityPos);
            if (building == null || !building.getColony().getPermissions().hasPermission(player, Action.ACCESS_HUTS))
            {
                player.displayClientMessage(Component.translatable("com.warehouseworkshop.research_supplies.invalid_university"), false);
                return;
            }
            final ResearchSuppliesModule module = building.getModule(ResearchSuppliesModule.class, ignored -> true);
            if (module == null)
            {
                player.displayClientMessage(Component.translatable("com.warehouseworkshop.research_supplies.invalid_university"), false);
                return;
            }
            ClientboundResearchSuppliesMessage.send(player, universityPos,
                ResearchSupplyPlanner.getStatuses(building, module.getPendingResearch(), module.getAutoStartResearch()));
        });
    }
}
