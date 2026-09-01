package com.deathfrog.warehouseworkshop.core.network;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.ResearchSuppliesModule;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import com.deathfrog.warehouseworkshop.core.research.ResearchSuppliesConstants;

/** Creates warehouse-to-university deliveries for one research. */
public record CreateResearchDeliveryMessage(BlockPos universityPos, ResourceLocation branch, ResourceLocation research, boolean autoStart)
    implements IServerboundPayload
{
    @SuppressWarnings("null")
    public static final Type<CreateResearchDeliveryMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, ResearchSuppliesConstants.CREATE_DELIVERY_PAYLOAD));
    
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateResearchDeliveryMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CreateResearchDeliveryMessage::universityPos,
        ResourceLocation.STREAM_CODEC, CreateResearchDeliveryMessage::branch,
        ResourceLocation.STREAM_CODEC, CreateResearchDeliveryMessage::research,
        net.minecraft.network.codec.ByteBufCodecs.BOOL, CreateResearchDeliveryMessage::autoStart,
        CreateResearchDeliveryMessage::new);

    /** Returns this payload's identifier. */
    @Override
    public Type<CreateResearchDeliveryMessage> type()
    {
        return ID;
    }

    /** Revalidates stock and creates exact deliveries on the server. */
    @SuppressWarnings("null")
    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            final IBuilding university = IColonyManager.getInstance().getBuilding(player.level(), universityPos);
            final IGlobalResearch global = IGlobalResearchTree.getInstance().getResearch(branch, research);
            if (university == null || global == null || !university.getColony().getPermissions().hasPermission(player, Action.ACCESS_HUTS)
                || !global.canResearch(university, university.getColony().getResearchManager().getResearchTree())
                || !IGlobalResearchTree.getInstance().isResearchRequirementsFulfilled(global.getResearchRequirements(), university.getColony()))
            {
                player.displayClientMessage(Component.translatable("com.warehouseworkshop.research_supplies.no_longer_eligible"), false);
                return;
            }
            final ResearchSuppliesModule module = university.getModule(ResearchSuppliesModule.class, ignored -> true);
            if (module == null || module.isPending(research))
            {
                player.displayClientMessage(Component.translatable("com.warehouseworkshop.research_supplies.already_requested"), false);
                return;
            }
            final List<ResearchSupplyPlanner.Allocation> allocations = ResearchSupplyPlanner.createAllocation(university, global);
            if (allocations.isEmpty() && !ResearchSupplyPlanner.universityHasCosts(university, global))
            {
                player.displayClientMessage(Component.translatable("com.warehouseworkshop.research_supplies.stock_changed"), false);
                return;
            }
            for (final ResearchSupplyPlanner.Allocation allocation : allocations)
            {
                university.createRequest(new Delivery(allocation.warehouse().getLocation(), university.getLocation(), allocation.stack(), 0), true);
            }
            module.addPending(branch, research, autoStart);
            player.displayClientMessage(Component.translatable(autoStart
                ? "com.warehouseworkshop.research_supplies.requested_start"
                : "com.warehouseworkshop.research_supplies.requested"), false);
            ClientboundResearchSuppliesMessage.send(player, universityPos,
                ResearchSupplyPlanner.getStatuses(university, module.getPendingResearch(), module.getAutoStartResearch()));
        });
    }
}
