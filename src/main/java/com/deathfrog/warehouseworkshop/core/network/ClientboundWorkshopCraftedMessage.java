package com.deathfrog.warehouseworkshop.core.network;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.client.network.ClientWorkshopCraftedHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Updates the open workshop window with authoritative post-craft stock.
 */
public record ClientboundWorkshopCraftedMessage(BlockPos buildingPos, List<ItemStack> warehouseStock, List<ItemStack> playerStock) implements CustomPacketPayload
{
    public static final Type<ClientboundWorkshopCraftedMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "workshop_crafted"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWorkshopCraftedMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        ClientboundWorkshopCraftedMessage::buildingPos,
        ItemStack.OPTIONAL_LIST_STREAM_CODEC,
        ClientboundWorkshopCraftedMessage::warehouseStock,
        ItemStack.OPTIONAL_LIST_STREAM_CODEC,
        ClientboundWorkshopCraftedMessage::playerStock,
        ClientboundWorkshopCraftedMessage::new);

    @Override
    public Type<ClientboundWorkshopCraftedMessage> type()
    {
        return ID;
    }

    public static void sendToPlayer(
        final ServerPlayer player,
        final BlockPos buildingPos,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory)
    {
        PacketDistributor.sendToPlayer(player, new ClientboundWorkshopCraftedMessage(
            buildingPos,
            aggregateInventory(warehouseInventory),
            aggregateInventory(playerInventory)));
    }

    public void onExecute(@NotNull final IPayloadContext context)
    {
        context.enqueueWork(() -> ClientWorkshopCraftedHandler.handle(buildingPos, warehouseStock, playerStock));
    }

    private static List<ItemStack> aggregateInventory(final IItemHandler inventory)
    {
        final List<ItemStack> stock = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty())
            {
                mergeStack(stock, stack);
            }
        }

        return stock;
    }

    private static void mergeStack(final List<ItemStack> stock, final ItemStack stack)
    {
        for (final ItemStack existing : stock)
        {
            if (ItemStack.isSameItemSameComponents(existing, stack))
            {
                existing.setCount(existing.getCount() + stack.getCount());
                return;
            }
        }

        stock.add(stack.copy());
    }
}
