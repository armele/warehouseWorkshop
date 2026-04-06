package com.deathfrog.warehouseworkshop.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule;
import com.deathfrog.warehouseworkshop.core.colony.buildings.modules.WorkshopModule.OutputTarget;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.InventoryUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Performs a validated warehouse-backed craft on the server.
 */
public record WorkshopCraftMessage(BlockPos buildingPos, List<ItemStack> grid, int craftCount, int craftType, ResourceLocation recipeId) implements IServerboundPayload
{
    public static final int CRAFT_TYPE_CRAFTING = 0;
    public static final int CRAFT_TYPE_DOMUM = 1;

    public static final Type<WorkshopCraftMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "workshop_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkshopCraftMessage> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        WorkshopCraftMessage::buildingPos,
        ItemStack.OPTIONAL_LIST_STREAM_CODEC,
        WorkshopCraftMessage::grid,
        ByteBufCodecs.INT,
        WorkshopCraftMessage::craftCount,
        ByteBufCodecs.INT,
        WorkshopCraftMessage::craftType,
        ResourceLocation.STREAM_CODEC,
        WorkshopCraftMessage::recipeId,
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
        final WorkshopModule module = building == null ? null : building.getModule(WorkshopModule.class, candidate -> true);
        if (building == null || module == null)
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

        if (craftType == CRAFT_TYPE_DOMUM)
        {
            executeDomumCraft(player, building, module, normalizedGrid);
            return;
        }

        if (craftType != CRAFT_TYPE_CRAFTING)
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        executeCraftingGridCraft(player, building, module, normalizedGrid);
    }

    private void executeCraftingGridCraft(
        final Player player,
        final IBuilding building,
        final WorkshopModule module,
        final List<ItemStack> normalizedGrid)
    {
        final CraftingInput input = CraftingInput.of(3, 3, normalizedGrid);
        final Optional<RecipeHolder<CraftingRecipe>> recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level());
        if (recipe.isEmpty() || !recipe.get().id().equals(recipeId))
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final List<ItemStack> baseIngredients = copyIngredients(normalizedGrid);
        final boolean includePlayerInventory = module.shouldIncludePlayerInventory();
        final IItemHandler warehouseInventory = building.getItemHandlerCap();
        final IItemHandler playerInventory = new PlayerMainInvWrapper(player.getInventory());
        if (!hasCraftingIngredients(copyIngredients(baseIngredients), warehouseInventory, playerInventory, includePlayerInventory))
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        final ItemStack craftedResult = recipe.get().value().assemble(input, player.level().registryAccess()).copy();
        final List<ItemStack> remainingItems = recipe.get().value().getRemainingItems(input);
        final OutputTarget outputTarget = module.getOutputTarget();
        int crafted = 0;

        for (int i = 0; i < craftCount; i++)
        {
            final List<ItemStack> ingredients = copyIngredients(baseIngredients);
            if (!hasCraftingIngredients(copyIngredients(ingredients), warehouseInventory, playerInventory, includePlayerInventory))
            {
                break;
            }

            if (!removeIngredients(ingredients, warehouseInventory, playerInventory, includePlayerInventory))
            {
                break;
            }

            giveCraftingOutput(player, warehouseInventory, craftedResult.copy(), outputTarget);
            for (final ItemStack remainder : remainingItems)
            {
                giveCraftingOutput(player, warehouseInventory, remainder.copy(), outputTarget);
            }
            crafted++;
        }

        if (crafted <= 0)
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.crafted", crafted), true);
        sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
    }

    private void executeDomumCraft(
        final Player player,
        final IBuilding building,
        final WorkshopModule module,
        final List<ItemStack> normalizedGrid)
    {
        final Optional<RecipeHolder<ArchitectsCutterRecipe>> recipe = player.level().getRecipeManager()
            .getAllRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER.get())
            .stream()
            .filter(candidate -> candidate.id().equals(recipeId))
            .findFirst();

        if (recipe.isEmpty())
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final List<IMateriallyTexturedBlockComponent> components = getDomumComponents(recipe.get().value());
        if (components.isEmpty() || components.size() > 2)
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final List<ItemStack> baseIngredients = getDomumIngredients(normalizedGrid, components.size());
        final ArchitectsCutterRecipeInput input = buildDomumInput(baseIngredients);
        if (!recipe.get().value().matches(input, player.level()))
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final ItemStack craftedResult = recipe.get().value().assemble(input, player.level().registryAccess()).copy();
        if (craftedResult.isEmpty())
        {
            player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.invalid"), true);
            return;
        }

        final boolean includePlayerInventory = module.shouldIncludePlayerInventory();
        final IItemHandler warehouseInventory = building.getItemHandlerCap();
        final IItemHandler playerInventory = new PlayerMainInvWrapper(player.getInventory());
        if (!hasCraftingIngredients(copyIngredients(baseIngredients), warehouseInventory, playerInventory, includePlayerInventory))
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        final OutputTarget outputTarget = module.getOutputTarget();
        int crafted = 0;
        for (int i = 0; i < craftCount; i++)
        {
            final List<ItemStack> ingredients = copyIngredients(baseIngredients);
            if (!hasCraftingIngredients(copyIngredients(ingredients), warehouseInventory, playerInventory, includePlayerInventory))
            {
                break;
            }

            if (!removeIngredients(ingredients, warehouseInventory, playerInventory, includePlayerInventory))
            {
                break;
            }

            giveCraftingOutput(player, warehouseInventory, craftedResult.copy(), outputTarget);
            crafted++;
        }

        if (crafted <= 0)
        {
            player.displayClientMessage(getMissingStatusText(includePlayerInventory), true);
            sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
            return;
        }

        player.displayClientMessage(Component.translatable("com.warehouseworkshop.core.gui.workshop.status.crafted", crafted), true);
        sendCraftingContentsSnapshot(player, warehouseInventory, playerInventory);
    }

    private void sendCraftingContentsSnapshot(
        final Player player,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory)
    {
        if (player instanceof final ServerPlayer serverPlayer)
        {
            ClientboundWorkshopCraftedMessage.sendToPlayer(serverPlayer, buildingPos, warehouseInventory, playerInventory);
        }
    }

    private static List<IMateriallyTexturedBlockComponent> getDomumComponents(final ArchitectsCutterRecipe recipe)
    {
        if (!(recipe.getBlock() instanceof final IMateriallyTexturedBlock texturedBlock))
        {
            return List.of();
        }

        return new ArrayList<>(texturedBlock.getComponents());
    }

    private static List<ItemStack> getDomumIngredients(final List<ItemStack> normalizedGrid, final int componentCount)
    {
        final List<ItemStack> ingredients = new ArrayList<>(componentCount);
        ingredients.add(normalizedGrid.get(1).copy());
        if (componentCount > 1)
        {
            ingredients.add(normalizedGrid.get(4).copy());
        }

        return ingredients;
    }

    private static ArchitectsCutterRecipeInput buildDomumInput(final List<ItemStack> ingredients)
    {
        final SimpleContainer container = new SimpleContainer(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++)
        {
            container.setItem(i, ingredients.get(i).copy());
        }

        return new ArchitectsCutterRecipeInput(container);
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

    private static boolean hasCraftingIngredients(
        final List<ItemStack> ingredients,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory,
        final boolean includePlayerInventory)
    {
        if (!includePlayerInventory)
        {
            return InventoryUtils.areAllItemsInItemHandler(ingredients, warehouseInventory);
        }

        final Map<ItemStack, Integer> required = countRequiredIngredients(ingredients);
        for (final Map.Entry<ItemStack, Integer> entry : required.entrySet())
        {
            final int available = countMatchingItems(warehouseInventory, entry.getKey()) + countMatchingItems(playerInventory, entry.getKey());
            if (available < entry.getValue())
            {
                return false;
            }
        }

        return true;
    }

    private static boolean removeIngredients(
        final List<ItemStack> ingredients,
        final IItemHandler warehouseInventory,
        final IItemHandler playerInventory,
        final boolean includePlayerInventory)
    {
        if (!includePlayerInventory)
        {
            return InventoryUtils.removeStacksFromItemHandler(warehouseInventory, ingredients);
        }

        for (final Map.Entry<ItemStack, Integer> entry : countRequiredIngredients(ingredients).entrySet())
        {
            int remaining = removeMatchingItems(warehouseInventory, entry.getKey(), entry.getValue());
            if (remaining > 0)
            {
                remaining = removeMatchingItems(playerInventory, entry.getKey(), remaining);
            }

            if (remaining > 0)
            {
                return false;
            }
        }

        return true;
    }

    private static Map<ItemStack, Integer> countRequiredIngredients(final List<ItemStack> ingredients)
    {
        final Map<ItemStack, Integer> required = new HashMap<>();
        for (final ItemStack ingredient : ingredients)
        {
            if (ingredient.isEmpty())
            {
                continue;
            }

            final ItemStack single = ingredient.copy();
            single.setCount(1);
            mergeRequiredIngredient(required, single, ingredient.getCount());
        }

        return required;
    }

    private static void mergeRequiredIngredient(final Map<ItemStack, Integer> required, final ItemStack ingredient, final int count)
    {
        for (final Map.Entry<ItemStack, Integer> entry : required.entrySet())
        {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), ingredient))
            {
                entry.setValue(entry.getValue() + count);
                return;
            }
        }

        required.put(ingredient.copy(), count);
    }

    private static int countMatchingItems(final IItemHandler inventory, final ItemStack target)
    {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target))
            {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static int removeMatchingItems(final IItemHandler inventory, final ItemStack target, final int count)
    {
        int remaining = count;
        for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target))
            {
                continue;
            }

            remaining -= inventory.extractItem(slot, remaining, false).getCount();
        }

        return remaining;
    }

    private static Component getMissingStatusText(final boolean includePlayerInventory)
    {
        return Component.translatable(includePlayerInventory
            ? "com.warehouseworkshop.core.gui.workshop.status.missing.include_player"
            : "com.warehouseworkshop.core.gui.workshop.status.missing");
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

    private static void giveCraftingOutput(final Player player, final IItemHandler warehouseInventory, final ItemStack stack, final OutputTarget outputTarget)
    {
        if (outputTarget.isWarehouse())
        {
            giveToWarehouse(player, warehouseInventory, stack);
            return;
        }

        giveToPlayer(player, stack);
    }

    private static void giveToWarehouse(final Player player, final IItemHandler warehouseInventory, final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }

        final ItemStack remainder = ItemHandlerHelper.insertItemStacked(warehouseInventory, stack, false);
        if (!remainder.isEmpty())
        {
            giveToPlayer(player, remainder);
        }
    }
}
