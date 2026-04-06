package com.deathfrog.warehouseworkshop.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.InventoryUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Performs a validated warehouse-backed craft on the server.
 */
public record WorkshopCraftMessage(BlockPos buildingPos, List<ItemStack> grid, int craftCount) implements IServerboundPayload
{
    public static final Type<WorkshopCraftMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "workshop_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkshopCraftMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        WorkshopCraftMessage::buildingPos,
        ItemStack.OPTIONAL_LIST_STREAM_CODEC,
        WorkshopCraftMessage::grid,
        ByteBufCodecs.INT,
        WorkshopCraftMessage::craftCount,
        WorkshopCraftMessage::new);

    @Override
    public Type<WorkshopCraftMessage> type()
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
        if (player == null || grid.size() != 9 || craftCount <= 0)
        {
            return;
        }

        final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), buildingPos);
        if (building == null || building.getModule(WorkshopModule.class, module -> true) == null)
        {
            return;
        }

        final List<ItemStack> normalizedGrid = new ArrayList<>(9);
        for (final ItemStack stack : grid)
        {
            if (stack == null || stack.isEmpty())
            {
                normalizedGrid.add(ItemStack.EMPTY);
                continue;
            }

            final ItemStack single = stack.copy();
            single.setCount(1);
            normalizedGrid.add(single);
        }

        final CraftingInput input = CraftingInput.of(3, 3, normalizedGrid);
        final Optional<RecipeHolder<CraftingRecipe>> recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level());
        if (recipe.isEmpty())
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final List<ItemStack> baseIngredients = copyIngredients(normalizedGrid);
        if (!InventoryUtils.areAllItemsInItemHandler(copyIngredients(baseIngredients), building.getItemHandlerCap()))
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.missing"), true);
            return;
        }

        final ItemStack craftedResult = recipe.get().value().assemble(input, player.level().registryAccess()).copy();
        final List<ItemStack> remainingItems = recipe.get().value().getRemainingItems(input);
        int crafted = 0;

        for (int i = 0; i < craftCount; i++)
        {
            final List<ItemStack> ingredients = copyIngredients(baseIngredients);
            if (!InventoryUtils.areAllItemsInItemHandler(copyIngredients(ingredients), building.getItemHandlerCap()))
            {
                break;
            }

            if (!InventoryUtils.removeStacksFromItemHandler(building.getItemHandlerCap(), ingredients))
            {
                break;
            }

            giveToPlayer(player, craftedResult.copy());
            for (final ItemStack remainder : remainingItems)
            {
                giveToPlayer(player, remainder.copy());
            }
            crafted++;
        }

        if (crafted <= 0)
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.missing"), true);
            return;
        }

        player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.crafted", crafted), true);
    }

    private static List<ItemStack> copyIngredients(final List<ItemStack> ingredients)
    {
        final List<ItemStack> copies = new ArrayList<>(ingredients.size());
        for (final ItemStack ingredient : ingredients)
        {
            if (!ingredient.isEmpty())
            {
                copies.add(ingredient.copy());
            }
        }
        return copies;
    }

    private static void giveToPlayer(final Player player, final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }

        final boolean added = player.getInventory().add(stack);
        if (!added && !stack.isEmpty())
        {
            player.drop(stack, false);
        }
    }
}
