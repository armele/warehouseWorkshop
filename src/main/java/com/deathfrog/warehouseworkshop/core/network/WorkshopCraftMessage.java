package com.deathfrog.warehouseworkshop.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

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

    @SuppressWarnings("null")
    public static final Type<WorkshopCraftMessage> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(WarehouseWorkshopMod.MODID, "workshop_craft"));
    
    @SuppressWarnings("null")
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

    /**
     * Executes a validated workshop craft on the server.
     * @param player The player performing the craft
     * @param building The building that the player is currently viewing
     * @param module The workshop module that is handling the craft
     * @param normalizedGrid The normalized 3x3 crafting grid
     */
    @SuppressWarnings("null")
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

    /**
     * Executes a validated grid-based craft on the server.
     * @param player the player performing the craft
     * @param building the building that the player is currently viewing
     * @param module the workshop module that is handling the craft
     * @param normalizedGrid the normalized 3x3 crafting grid
     */
    @SuppressWarnings("null")
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

    /**
     * Executes a validated Domum craft on the server.
     * @param player The player performing the craft
     * @param building The building that the player is currently viewing
     * @param module The workshop module that is handling the craft
     * @param normalizedGrid The normalized 3x3 crafting grid
     */
    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
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

    /**
     * Checks if all the ingredients in the given list are available in the given item handlers.
     * If includePlayerInventory is true, the player's inventory is also checked.
     * 
     * @param ingredients the list of ingredients to check
     * @param warehouseInventory the item handler for the warehouse inventory
     * @param playerInventory the item handler for the player inventory
     * @param includePlayerInventory whether to include the player inventory in the check
     * @return true if all ingredients are available, false otherwise
     */
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
            ItemStack key = entry.getKey();

            if (key == null || key.isEmpty())
            {
                continue;
            }

            final int available = countMatchingItems(warehouseInventory, key) + countMatchingItems(playerInventory, key);
            if (available < entry.getValue())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Removes the given ingredients from the given item handlers.
     * If includePlayerInventory is true, both the warehouse and player inventory are checked.
     * 
     * @param ingredients the list of ingredients to remove
     * @param warehouseInventory the item handler for the warehouse inventory
     * @param playerInventory the item handler for the player inventory
     * @param includePlayerInventory whether to include the player inventory in the removal
     * @return true if all ingredients were removed, false otherwise
     */
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
            ItemStack key = entry.getKey();

            if (key == null || key.isEmpty())
            {
                continue;
            }

            int remaining = removeMatchingItems(warehouseInventory, key, entry.getValue());
            if (remaining > 0)
            {
                remaining = removeMatchingItems(playerInventory, key, remaining);
            }

            if (remaining > 0)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Counts the number of required ingredients for the given list of ingredients.
     * 
     * The method iterates over the given list of ingredients, and for each ingredient,
     * it creates a new ItemStack with a count of 1 and merges it into the given map
     * with the count of the original ingredient. If the map already contains the
     * ingredient, the count is added to the existing value. Otherwise, the ingredient is
     * added to the map with the given count.
     * 
     * @param ingredients the list of ingredients to count
     * @return a map of ingredients to their required counts
     */
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

    /**
     * Merges the given ingredient and count into the given map of required ingredients.
     * If the map already contains the given ingredient, the count is added to the existing value.
     * Otherwise, the ingredient is added to the map with the given count.
     *
     * @param required the map of required ingredients to merge into
     * @param ingredient the ingredient to merge
     * @param count the count of the ingredient to merge
     */
    private static void mergeRequiredIngredient(final Map<ItemStack, Integer> required, final @Nonnull ItemStack ingredient, final int count)
    {
        for (final Map.Entry<ItemStack, Integer> entry : required.entrySet())
        {
            ItemStack key = entry.getKey();
            if (key == null)
            {
                continue;
            }

            if (ItemStack.isSameItemSameComponents(key, ingredient))
            {
                entry.setValue(entry.getValue() + count);
                return;
            }
        }

        required.put(ingredient.copy(), count);
    }

    /**
     * Counts the number of matching items in the given inventory.
     * The method iterates over all slots in the inventory and checks if the stack in the slot
     * matches the given target item (ignoring damage value and NBT). If the stack matches, its count is added to the total count.
     *
     * @param inventory the inventory to count matching items in
     * @param target the target item to count
     * @return the total count of matching items in the inventory
     */
    private static int countMatchingItems(final IItemHandler inventory, final @Nonnull ItemStack target)
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

    /**
     * Removes matching items from the given inventory.
     * The method iterates over all slots in the inventory and checks if the stack in the slot
     * matches the given target item (ignoring damage value and NBT). If the stack matches, it is removed
     * from the inventory up to the given count.
     *
     * @param inventory the inventory to remove matching items from
     * @param target the target item to remove
     * @param count the maximum number of items to remove
     * @return the number of items remaining after the removal
     */
    private static int removeMatchingItems(final IItemHandler inventory, final @Nonnull ItemStack target, final int count)
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

    private static void giveCraftingOutput(final Player player, final @Nonnull IItemHandler warehouseInventory, final ItemStack stack, final OutputTarget outputTarget)
    {
        if (outputTarget.isWarehouse())
        {
            giveToWarehouse(player, warehouseInventory, stack);
            return;
        }

        giveToPlayer(player, stack);
    }

    private static void giveToWarehouse(final Player player, final @Nonnull IItemHandler warehouseInventory, final ItemStack stack)
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
