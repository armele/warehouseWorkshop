package com.deathfrog.warehouseworkshop.core.network;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.client.ResearchSuppliesClientHooks;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.CostStatus;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.ResearchStatus;
import com.deathfrog.warehouseworkshop.core.research.ResearchSupplyPlanner.StartState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.warehouseworkshop.core.research.ResearchSuppliesConstants;

/** Sends authoritative research supply rows to the active client window. */
public record ClientboundResearchSuppliesMessage(BlockPos universityPos, List<ResearchStatus> statuses) implements CustomPacketPayload
{
    @SuppressWarnings("null")
    public static final Type<ClientboundResearchSuppliesMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, ResearchSuppliesConstants.SNAPSHOT_PAYLOAD));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundResearchSuppliesMessage> STREAM_CODEC = StreamCodec.of(
        ClientboundResearchSuppliesMessage::encode, ClientboundResearchSuppliesMessage::decode);

    /** Encodes a research-supply snapshot. */
    @SuppressWarnings("null")
    private static void encode(final RegistryFriendlyByteBuf buffer, final ClientboundResearchSuppliesMessage message)
    {
        BlockPos.STREAM_CODEC.encode(buffer, message.universityPos());
        buffer.writeVarInt(message.statuses().size());
        for (final ResearchStatus status : message.statuses())
        {
            buffer.writeResourceLocation(status.branch());
            buffer.writeResourceLocation(status.research());
            buffer.writeUtf(status.name());
            buffer.writeVarInt(status.depth());
            buffer.writeBoolean(status.deliverable());
            buffer.writeBoolean(status.pending());
            buffer.writeBoolean(status.autoStart());
            buffer.writeEnum(status.startState());
            buffer.writeVarInt(status.activeResearch());
            buffer.writeVarInt(status.researchCapacity());
            buffer.writeVarInt(status.costs().size());
            for (final CostStatus cost : status.costs())
            {
                ItemStack.STREAM_CODEC.encode(buffer, cost.display());
                buffer.writeVarInt(cost.required());
                buffer.writeVarInt(cost.university());
                buffer.writeVarInt(cost.warehouse());
            }
        }
    }

    /** Decodes a research-supply snapshot. */
    private static ClientboundResearchSuppliesMessage decode(final @Nonnull RegistryFriendlyByteBuf buffer)
    {
        final BlockPos position = BlockPos.STREAM_CODEC.decode(buffer);
        final List<ResearchStatus> statuses = new ArrayList<>();
        for (int i = buffer.readVarInt(); i > 0; i--)
        {
            final ResourceLocation branch = buffer.readResourceLocation();
            final ResourceLocation research = buffer.readResourceLocation();
            final String name = buffer.readUtf();
            final int depth = buffer.readVarInt();
            final boolean deliverable = buffer.readBoolean();
            final boolean pending = buffer.readBoolean();
            final boolean autoStart = buffer.readBoolean();
            final StartState startState = buffer.readEnum(StartState.class);
            final int activeResearch = buffer.readVarInt();
            final int researchCapacity = buffer.readVarInt();
            final List<CostStatus> costs = new ArrayList<>();
            for (int j = buffer.readVarInt(); j > 0; j--)
            {
                costs.add(new CostStatus(ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
            }
            statuses.add(new ResearchStatus(branch, research, name, depth, costs, deliverable, pending, autoStart,
                startState, activeResearch, researchCapacity));
        }
        return new ClientboundResearchSuppliesMessage(position, statuses);
    }

    /** Returns this payload's identifier. */
    @Override
    public Type<ClientboundResearchSuppliesMessage> type()
    {
        return ID;
    }

    /** Applies the snapshot on the client thread. */
    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> ResearchSuppliesClientHooks.handleSnapshot(universityPos, statuses));
    }

    /** Sends a snapshot to one player. */
    public static void send(final @Nonnull ServerPlayer player, final BlockPos position, final List<ResearchStatus> statuses)
    {
        PacketDistributor.sendToPlayer(player, new ClientboundResearchSuppliesMessage(position, statuses));
    }
}
